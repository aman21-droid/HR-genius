package com.hrgenius.payroll;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 end to end on its own H2 database: seeded history, a new month's run with an adjustment
 * and loss-of-pay leave, approval, publication to employees, exports and access rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_payroll;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-payroll"})
class PayrollApiIntegrationTest {

    private static final String PAYROLL = "payroll@hrgenius.com";
    private static final String HR = "hr@hrgenius.com";
    private static final String EMPLOYEE = "employee@hrgenius.com";
    private static final String MANAGER = "manager@hrgenius.com";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final Map<String, String> tokens = new HashMap<>();

    private String bearer(String email) throws Exception {
        String t = tokens.get(email);
        if (t == null) {
            String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "Password@123"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            t = JsonPath.read(body, "$.accessToken");
            tokens.put(email, t);
        }
        return "Bearer " + t;
    }

    private String call(MockHttpServletRequestBuilder req, String email, Object payload, int expected) throws Exception {
        req = req.header("Authorization", bearer(email));
        if (payload != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        }
        return mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private byte[] bytes(MockHttpServletRequestBuilder req, String email) throws Exception {
        return mvc.perform(req.header("Authorization", bearer(email))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private long id(String sql, Object... args) {
        return ((Number) jdbc.queryForObject(sql, Long.class, args)).longValue();
    }

    private void approveWholeChain(long approvalId) throws Exception {
        for (int guard = 0; guard < 5; guard++) {
            String st = jdbc.queryForObject("SELECT status FROM approval_requests WHERE id = ?", String.class, approvalId);
            if (!"PENDING".equals(st)) {
                return;
            }
            Map<String, Object> step = jdbc.queryForMap("SELECT s.id AS id, u.email AS email FROM approval_steps s "
                    + "JOIN approval_requests r ON r.id = s.request_id JOIN users u ON u.employee_id = s.approver_emp_id "
                    + "WHERE s.request_id = ? AND s.step_no = r.current_step", approvalId);
            call(post("/api/v1/approvals/steps/{id}/decide", ((Number) step.get("id")).longValue()), (String) step.get("email"),
                    Map.of("approve", true), 200);
        }
    }

    @Test
    void seededHistoryIsVisibleToEmployeesAndProtected() throws Exception {
        String runs = call(get("/api/v1/payroll/runs"), PAYROLL, null, 200);
        assertThat(JsonPath.<List<String>>read(runs, "$[*].period")).containsExactly("2026-09", "2026-08");
        assertThat(JsonPath.<List<String>>read(runs, "$[*].status")).containsExactly("APPROVED", "PAID");
        // Seeded totals are consistent: net = gross - deductions.
        BigDecimal g = new BigDecimal(JsonPath.read(runs, "$[1].totalGross").toString());
        BigDecimal d = new BigDecimal(JsonPath.read(runs, "$[1].totalDeductions").toString());
        BigDecimal n = new BigDecimal(JsonPath.read(runs, "$[1].totalNet").toString());
        assertThat(g.subtract(d)).isEqualByComparingTo(n);

        String mine = call(get("/api/v1/payroll/me/payslips"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<String>>read(mine, "$[*].period")).containsExactly("2026-09", "2026-08");
        long mySlip = ((Number) JsonPath.read(mine, "$[0].id")).longValue();
        String slip = call(get("/api/v1/payroll/payslips/{id}", mySlip), EMPLOYEE, null, 200);
        assertThat(JsonPath.<String>read(slip, "$.employeeName")).isEqualTo("Emma Lopez");
        assertThat(JsonPath.<String>read(slip, "$.accountMasked")).startsWith("X");      // masked, never plain
        assertThat(JsonPath.<String>read(slip, "$.pan")).startsWith("X");
        byte[] pdf = bytes(get("/api/v1/payroll/payslips/{id}/pdf", mySlip), EMPLOYEE);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // Someone else's payslip is off limits; the payroll team and staff API are gated.
        long managerSlip = id("SELECT p.id FROM payslips p JOIN employees e ON e.id = p.employee_id "
                + "JOIN payroll_runs r ON r.id = p.run_id WHERE e.work_email = ? AND r.period = '2026-09'", MANAGER);
        call(get("/api/v1/payroll/payslips/{id}", managerSlip), EMPLOYEE, null, 403);
        call(get("/api/v1/payroll/runs"), EMPLOYEE, null, 403);
        call(get("/api/v1/payroll/runs"), HR, null, 403);

        // Own structure is derived from CTC.
        String structure = call(get("/api/v1/payroll/me/structure"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<String>>read(structure, "$.earnings[*].code")).contains("BASIC", "HRA", "SPECIAL");
    }

    @Test
    void monthlyRunFromDraftToPaid() throws Exception {
        long emmaId = id("SELECT id FROM employees WHERE work_email = ?", EMPLOYEE);

        // Emma takes one day of approved loss-of-pay leave on Wed 14 Oct 2026.
        long lwp = id("SELECT id FROM leave_types WHERE code = 'LWP'");
        String leave = call(post("/api/v1/leave/requests"), EMPLOYEE, Map.of(
                "leaveTypeId", lwp, "startDate", "2026-10-14", "endDate", "2026-10-14", "reason", "Personal"), 201);
        approveWholeChain(((Number) JsonPath.read(leave, "$.approvalRequestId")).longValue());

        String run = call(post("/api/v1/payroll/runs"), PAYROLL, Map.of("period", "2026-10"), 201);
        long runId = ((Number) JsonPath.read(run, "$.id")).longValue();
        call(post("/api/v1/payroll/runs"), PAYROLL, Map.of("period", "2026-10"), 409);      // one run per month
        call(post("/api/v1/payroll/runs/{id}/submit", runId), PAYROLL, null, 409);            // not calculated yet

        String calc = call(post("/api/v1/payroll/runs/{id}/calculate", runId), PAYROLL, null, 200);
        assertThat(JsonPath.<String>read(calc, "$.status")).isEqualTo("CALCULATED");
        int eligible = jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE deleted = 0 AND annual_ctc > 0 "
                + "AND date_of_joining <= DATE '2026-10-31' AND (exit_date IS NULL OR exit_date >= DATE '2026-10-01')", Integer.class);
        assertThat(JsonPath.<Integer>read(calc, "$.employeeCount")).isEqualTo(eligible);

        Map<String, Object> emma = jdbc.queryForMap("SELECT lop_days, paid_days, days_in_period FROM payslips "
                + "WHERE run_id = ? AND employee_id = ?", runId, emmaId);
        assertThat(new BigDecimal(emma.get("lop_days").toString())).isEqualByComparingTo("1");
        assertThat(new BigDecimal(emma.get("paid_days").toString())).isEqualByComparingTo("30");

        // A bonus returns the run to draft; recalculation includes it.
        call(post("/api/v1/payroll/runs/{id}/adjustments", runId), PAYROLL, Map.of(
                "employeeId", emmaId, "type", "EARNING", "label", "Spot award", "amount", 5000), 201);
        assertThat(JsonPath.<String>read(call(get("/api/v1/payroll/runs/{id}", runId), PAYROLL, null, 200), "$.status"))
                .isEqualTo("DRAFT");
        call(post("/api/v1/payroll/runs/{id}/calculate", runId), PAYROLL, null, 200);
        long emmaSlip = id("SELECT id FROM payslips WHERE run_id = ? AND employee_id = ?", runId, emmaId);
        String slip = call(get("/api/v1/payroll/payslips/{id}", emmaSlip), PAYROLL, null, 200);
        assertThat(JsonPath.<List<String>>read(slip, "$.earnings[*].name")).contains("Spot award");

        // Not visible to Emma before approval.
        call(get("/api/v1/payroll/payslips/{id}", emmaSlip), EMPLOYEE, null, 403);
        assertThat(JsonPath.<List<Object>>read(call(get("/api/v1/payroll/me/payslips"), EMPLOYEE, null, 200), "$")).hasSize(2);

        byte[] register = bytes(get("/api/v1/payroll/runs/{id}/register", runId), PAYROLL);
        assertThat(new String(register, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");     // xlsx = zip
        call(get("/api/v1/payroll/runs/{id}/bank-file", runId), PAYROLL, null, 409);          // not approved yet

        // Approval by HR (holder of PAYROLL_APPROVE) publishes it.
        String submitted = call(post("/api/v1/payroll/runs/{id}/submit", runId), PAYROLL, null, 200);
        long approvalId = ((Number) JsonPath.read(submitted, "$.approvalRequestId")).longValue();
        String approver = jdbc.queryForObject("SELECT u.email FROM approval_steps s JOIN users u ON u.employee_id = s.approver_emp_id "
                + "WHERE s.request_id = ? AND s.step_no = 1", String.class, approvalId);
        assertThat(approver).isEqualTo(HR);
        call(post("/api/v1/payroll/runs/{id}/adjustments", runId), PAYROLL, Map.of(
                "employeeId", emmaId, "type", "EARNING", "label", "Late", "amount", 1), 409);   // frozen while pending
        approveWholeChain(approvalId);
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/payroll/me/payslips"), EMPLOYEE, null, 200), "$[*].period"))
                .containsExactly("2026-10", "2026-09", "2026-08");

        int auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
        byte[] bank = bytes(get("/api/v1/payroll/runs/{id}/bank-file", runId), PAYROLL);
        assertThat(new String(bank, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class)).isEqualTo(auditBefore + 1);

        String paid = call(post("/api/v1/payroll/runs/{id}/paid", runId), PAYROLL, Map.of("paymentReference", "NEFT-OCT"), 200);
        assertThat(JsonPath.<String>read(paid, "$.status")).isEqualTo("PAID");
        call(delete("/api/v1/payroll/runs/{id}", runId), PAYROLL, null, 409);                 // paid runs are permanent
    }

    @Test
    void salaryStructureGuardsItsRequiredComponents() throws Exception {
        String comps = call(get("/api/v1/payroll/components"), PAYROLL, null, 200);
        long basicId = ((Number) JsonPath.<List<Integer>>read(comps, "$[?(@.code == 'BASIC')].id").get(0)).longValue();
        call(delete("/api/v1/payroll/components/{id}", basicId), PAYROLL, null, 409);
        // A second balancing component would make the structure ambiguous.
        call(post("/api/v1/payroll/components"), PAYROLL, Map.of("code", "FLEX", "name", "Flexi", "calcType", "BALANCING",
                "calcValue", 0, "sortOrder", 95), 409);
        // A fixed allowance is fine.
        call(post("/api/v1/payroll/components"), PAYROLL, Map.of("code", "INTERNET", "name", "Internet allowance",
                "calcType", "FIXED_MONTHLY", "calcValue", 1000, "sortOrder", 40), 201);
    }
}
