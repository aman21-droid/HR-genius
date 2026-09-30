package com.hrgenius.leave;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for Phase 3: leave apply -> multi-step approval -> balance movement,
 * and attendance punches. Runs the real migrations on its own H2 (Oracle mode) database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_leave;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-leave"})
class LeaveAttendanceApiIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + token(email);
    }

    private BigDecimal casualAvailable(String employeeEmail) throws Exception {
        // Read via the employee's own balances endpoint.
        String body = mvc.perform(get("/api/v1/leave/balances").header("Authorization", bearer(employeeEmail)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> rows = json.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        return rows.stream().filter(r -> "CL".equals(r.get("code"))).findFirst()
                .map(r -> new BigDecimal(r.get("available").toString())).orElseThrow();
    }

    /** Walk the pending steps of an approval request, approving each as its assigned approver. */
    private void approveWholeChain(long approvalRequestId) throws Exception {
        for (int guard = 0; guard < 10; guard++) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM approval_requests WHERE id = ?", String.class, approvalRequestId);
            if (!"PENDING".equals(status)) {
                return;
            }
            Map<String, Object> step = jdbc.queryForMap(
                    "SELECT s.id AS id, s.approver_emp_id AS emp FROM approval_steps s "
                            + "JOIN approval_requests r ON r.id = s.request_id "
                            + "WHERE s.request_id = ? AND s.step_no = r.current_step AND s.status = 'PENDING'",
                    approvalRequestId);
            Long stepId = ((Number) step.get("id")).longValue();
            Long approverEmp = ((Number) step.get("emp")).longValue();
            String approverEmail = jdbc.queryForObject(
                    "SELECT email FROM users WHERE employee_id = ?", String.class, approverEmp);
            assertThat(approverEmail).as("approver must have a login").isNotNull();

            mvc.perform(post("/api/v1/approvals/steps/{id}/decide", stepId)
                            .header("Authorization", bearer(approverEmail))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("approve", true, "comment", "ok"))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void leaveApplyRoutesToManagerThenHrAndMovesBalance() throws Exception {
        String employee = "employee@hrgenius.com"; // Emma Lopez, reports to manager@ (Manuel)
        BigDecimal before = casualAvailable(employee);

        // Apply for 2 working days of Casual Leave (Tue-Wed, no holiday).
        String applyBody = mvc.perform(post("/api/v1/leave/requests")
                        .header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "leaveTypeId", casualTypeId(),
                                "startDate", "2026-02-10",
                                "endDate", "2026-02-11",
                                "reason", "Family function"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(applyBody, "$.days").toString())).isEqualByComparingTo("2");
        long approvalId = ((Number) JsonPath.read(applyBody, "$.approvalRequestId")).longValue();

        // Days are reserved as pending, so available drops by 2 immediately.
        assertThat(casualAvailable(employee)).isEqualByComparingTo(before.subtract(new BigDecimal("2")));

        // Manager sees it in the inbox.
        mvc.perform(get("/api/v1/approvals/inbox").header("Authorization", bearer("manager@hrgenius.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.subjectType == 'LEAVE')]", not(empty())));

        // Approve every step (manager -> HR).
        approveWholeChain(approvalId);

        // Leave is APPROVED; pending converted to used, so available is unchanged from the reservation.
        String requests = mvc.perform(get("/api/v1/leave/requests").header("Authorization", bearer(employee)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(requests, "$[0].status")).isEqualTo("APPROVED");
        assertThat(casualAvailable(employee)).isEqualByComparingTo(before.subtract(new BigDecimal("2")));

        String usedStr = jdbc.queryForObject(
                "SELECT b.used FROM leave_balances b JOIN leave_types t ON t.id = b.leave_type_id "
                        + "JOIN employees e ON e.id = b.employee_id "
                        + "WHERE e.work_email = ? AND t.code = 'CL' AND b.year_no = 2026",
                String.class, employee);
        assertThat(new BigDecimal(usedStr)).isEqualByComparingTo("2");
    }

    @Test
    void insufficientBalanceIsRejected() throws Exception {
        // Sick Leave entitlement is 10; ask for far more working days than available.
        long slTypeId = ((Number) jdbc.queryForObject(
                "SELECT id FROM leave_types WHERE code = 'SL'", Long.class)).longValue();
        mvc.perform(post("/api/v1/leave/requests")
                        .header("Authorization", bearer("employee@hrgenius.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "leaveTypeId", slTypeId,
                                "startDate", "2026-03-09",   // Mon
                                "endDate", "2026-03-27",     // 15 working days
                                "reason", "Too much"))))
                .andExpect(status().isConflict());
    }

    @Test
    void attendanceCheckInAndCheckOutRecordsTheDay() throws Exception {
        String auth = bearer("employee@hrgenius.com");
        mvc.perform(post("/api/v1/attendance/check-in").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PRESENT")))
                .andExpect(jsonPath("$.checkIn", notNullValue()));

        // Second check-in the same day is refused.
        mvc.perform(post("/api/v1/attendance/check-in").header("Authorization", auth))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/attendance/check-out").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkOut", notNullValue()))
                .andExpect(jsonPath("$.status", anyOf(is("PRESENT"), is("HALF_DAY"))));
    }

    @Test
    void leaveTypeConfigIsGuardedAndListsSeededTypes() throws Exception {
        // Any authenticated user can list types (5 seeded).
        mvc.perform(get("/api/v1/leave/types").header("Authorization", bearer("employee@hrgenius.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(5)));

        // A plain employee cannot create a type.
        mvc.perform(post("/api/v1/leave/types")
                        .header("Authorization", bearer("employee@hrgenius.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "code", "WFH", "name", "Work From Home",
                                "annualEntitlement", 0, "accrualMethod", "NONE",
                                "accrualRate", 0, "carryForwardCap", 0))))
                .andExpect(status().isForbidden());

        // HR admin can.
        mvc.perform(post("/api/v1/leave/types")
                        .header("Authorization", bearer("hr@hrgenius.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "code", "WFH", "name", "Work From Home",
                                "annualEntitlement", 0, "accrualMethod", "NONE",
                                "accrualRate", 0, "carryForwardCap", 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("WFH")));
    }

    private long casualTypeId() {
        return ((Number) jdbc.queryForObject("SELECT id FROM leave_types WHERE code = 'CL'", Long.class)).longValue();
    }
}
