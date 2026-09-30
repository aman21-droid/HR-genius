package com.hrgenius.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 4 flow on its own H2 (Oracle mode) database: requisition approval, a public
 * careers application, pipeline moves, an interview with feedback, offer approval, conversion to an
 * employee, and the generated onboarding plan. Also checks the access rules around each step.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_recruit;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-recruit",
        "hrgenius.careers.max-applications-per-hour=4"})
class RecruitmentApiIntegrationTest {

    private static final String PASSWORD = "Password@123";
    private static final String RECRUITER = "recruiter@hrgenius.com";   // Riya Chen: RECRUITMENT_MANAGE only
    private static final String HR = "hr@hrgenius.com";                 // Hana Reddy: HR_ADMIN
    private static final String MANAGER = "manager@hrgenius.com";       // Manuel Garcia: hiring manager
    private static final String EMPLOYEE = "employee@hrgenius.com";     // Emma Lopez: will sit on the panel
    private static final byte[] PDF = "%PDF-1.4\n% test resume\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final Map<String, String> tokens = new HashMap<>();

    private String bearer(String email) throws Exception {
        String token = tokens.get(email);
        if (token == null) {
            String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            token = JsonPath.read(body, "$.accessToken");
            tokens.put(email, token);
        }
        return "Bearer " + token;
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String email) throws Exception {
        return req.header("Authorization", bearer(email));
    }

    private String body(MockHttpServletRequestBuilder req, String email, Object payload, int expected) throws Exception {
        MockHttpServletRequestBuilder r = as(req, email);
        if (payload != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private long id(String sql, Object... args) {
        return ((Number) jdbc.queryForObject(sql, Long.class, args)).longValue();
    }

    private String stepApprover(long approvalRequestId) {
        return jdbc.queryForObject(
                "SELECT u.email FROM approval_steps s JOIN approval_requests r ON r.id = s.request_id "
                        + "JOIN users u ON u.employee_id = s.approver_emp_id "
                        + "WHERE s.request_id = ? AND s.step_no = r.current_step AND s.status = 'PENDING'",
                String.class, approvalRequestId);
    }

    /** Approves every pending step of a flow as its assigned approver; returns the approvers in order. */
    private List<String> approveWholeChain(long approvalRequestId) throws Exception {
        List<String> approvers = new java.util.ArrayList<>();
        for (int guard = 0; guard < 10; guard++) {
            String status = jdbc.queryForObject("SELECT status FROM approval_requests WHERE id = ?", String.class,
                    approvalRequestId);
            if (!"PENDING".equals(status)) {
                return approvers;
            }
            long stepId = id("SELECT s.id FROM approval_steps s JOIN approval_requests r ON r.id = s.request_id "
                    + "WHERE s.request_id = ? AND s.step_no = r.current_step", approvalRequestId);
            String approver = stepApprover(approvalRequestId);
            approvers.add(approver);
            body(post("/api/v1/approvals/steps/{id}/decide", stepId), approver, Map.of("approve", true), 200);
        }
        throw new AssertionError("approval chain did not resolve");
    }

    private MockHttpServletRequestBuilder careersApply(String reqCode, String email, String website) {
        var req = multipart("/api/v1/public/careers/jobs/{code}/apply", reqCode)
                .file(new MockMultipartFile("resume", "cv.pdf", "application/pdf", PDF))
                .param("firstName", "Zara").param("lastName", "Khan").param("email", email)
                .param("phone", "+91 9876500000").param("currentTitle", "Backend Engineer")
                .param("totalExperience", "5").param("coverNote", "Excited about HRGenius");
        if (website != null) {
            req.param("website", website);
        }
        return req;
    }

    @Test
    void fullHiringFlowFromRequisitionToOnboarding() throws Exception {
        long eng = id("SELECT id FROM departments WHERE code = 'ENG'");
        long se = id("SELECT id FROM designations WHERE code = 'SE'");
        long blr = id("SELECT id FROM locations WHERE code = 'BLR'");
        long managerId = id("SELECT id FROM employees WHERE work_email = ?", MANAGER);
        long panelistId = id("SELECT id FROM employees WHERE work_email = ?", EMPLOYEE);

        // ---- 1. Recruiter drafts a requisition; it is not public yet.
        Map<String, Object> reqBody = new HashMap<>(Map.of(
                "title", "Software Engineer (Platform)", "departmentId", eng, "designationId", se,
                "locationId", blr, "hiringManagerId", managerId, "employmentType", "FULL_TIME",
                "openings", 1, "skills", "Java, Kubernetes", "description", "Platform team role"));
        reqBody.put("salaryMin", 1500000);
        reqBody.put("salaryMax", 2200000);
        String created = body(post("/api/v1/recruitment/requisitions"), RECRUITER, reqBody, 201);
        long reqId = ((Number) JsonPath.read(created, "$.id")).longValue();
        String reqCode = JsonPath.read(created, "$.reqCode");
        assertThat(reqCode).matches("REQ-\\d{4}");
        assertThat(JsonPath.<String>read(created, "$.status")).isEqualTo("DRAFT");
        mvc.perform(get("/api/v1/public/careers/jobs/{c}", reqCode)).andExpect(status().isNotFound());

        // ---- 2. Submit -> hiring manager, then HR (the HR admin, not the super-admin) -> OPEN.
        String submitted = body(post("/api/v1/recruitment/requisitions/{id}/submit", reqId), RECRUITER, null, 200);
        assertThat(JsonPath.<String>read(submitted, "$.status")).isEqualTo("PENDING_APPROVAL");
        long reqApproval = ((Number) JsonPath.read(submitted, "$.approvalRequestId")).longValue();
        assertThat(approveWholeChain(reqApproval)).containsExactly(MANAGER, HR);
        assertThat(JsonPath.<String>read(body(get("/api/v1/recruitment/requisitions/{id}", reqId), RECRUITER, null, 200),
                "$.status")).isEqualTo("OPEN");

        // ---- 3. Public careers page: listed without salary; apply anonymously.
        String jobs = mvc.perform(get("/api/v1/public/careers/jobs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(jobs, "$[*].reqCode")).contains(reqCode);
        assertThat(jobs).doesNotContain("salary").doesNotContain("2200000");

        mvc.perform(careersApply(reqCode, "zara.khan@example.com", null)).andExpect(status().isAccepted());
        // Same person again: identical response, no duplicate application.
        mvc.perform(careersApply(reqCode, "ZARA.KHAN@example.com", null)).andExpect(status().isAccepted());
        // Bot filled the honeypot: accepted-looking response, nothing stored.
        mvc.perform(careersApply(reqCode, "bot@example.com", "http://spam")).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidates WHERE email = 'bot@example.com'", Integer.class))
                .isZero();
        // A resume that is not really a PDF is rejected by the magic-byte check.
        mvc.perform(multipart("/api/v1/public/careers/jobs/{code}/apply", reqCode)
                        .file(new MockMultipartFile("resume", "cv.pdf", "application/pdf", "<html>".getBytes()))
                        .param("firstName", "Eve").param("lastName", "X").param("email", "eve@example.com"))
                .andExpect(status().isBadRequest());
        // Fifth attempt from this client within the hour is throttled (limit 4 in this test).
        mvc.perform(careersApply(reqCode, "late@example.com", null)).andExpect(status().isTooManyRequests());

        // ---- 4. Pipeline shows the careers applicant once, at APPLIED.
        String pipeline = body(get("/api/v1/recruitment/requisitions/{id}/pipeline", reqId), RECRUITER, null, 200);
        assertThat(JsonPath.<List<String>>read(pipeline, "$.applications[*].email")).containsExactly("zara.khan@example.com");
        assertThat(JsonPath.<String>read(pipeline, "$.applications[0].source")).isEqualTo("CAREERS_PAGE");
        long appId = ((Number) JsonPath.read(pipeline, "$.applications[0].id")).longValue();
        long candidateId = ((Number) JsonPath.read(pipeline, "$.applications[0].candidateId")).longValue();

        // Stage rules: rejecting needs a reason; HIRED is only reachable via the hire flow.
        body(post("/api/v1/recruitment/applications/{id}/stage", appId), RECRUITER, Map.of("stage", "REJECTED"), 400);
        body(post("/api/v1/recruitment/applications/{id}/stage", appId), RECRUITER, Map.of("stage", "HIRED"), 409);
        body(post("/api/v1/recruitment/applications/{id}/stage", appId), RECRUITER, Map.of("stage", "SCREENING"), 200);

        // ---- 5. Interview with Emma on the panel -> application moves to INTERVIEW.
        String when = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        String interview = body(post("/api/v1/recruitment/applications/{id}/interviews", appId), RECRUITER,
                Map.of("roundName", "Technical 1", "mode", "VIDEO", "scheduledAt", when, "durationMinutes", 60,
                        "locationOrLink", "https://meet.example.com/x", "panelistIds", List.of(panelistId)), 201);
        long interviewId = ((Number) JsonPath.read(interview, "$.id")).longValue();
        assertThat(JsonPath.<String>read(body(get("/api/v1/recruitment/applications/{id}", appId), RECRUITER, null, 200),
                "$.stage")).isEqualTo("INTERVIEW");

        // Panelist sees it under "my interviews" and may read the resume; an unrelated employee may not.
        String mine = body(get("/api/v1/recruitment/interviews/mine"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<Integer>>read(mine, "$[?(@.interviewId == " + interviewId + ")].interviewId")).hasSize(1);
        mvc.perform(as(get("/api/v1/recruitment/candidates/{id}/resume", candidateId), EMPLOYEE))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/recruitment/candidates/{id}/resume", candidateId), "payroll@hrgenius.com"))
                .andExpect(status().isForbidden());
        // Panelists cannot use the recruiter API.
        mvc.perform(as(get("/api/v1/recruitment/requisitions"), EMPLOYEE)).andExpect(status().isForbidden());

        // Only panelists can submit feedback; the sole panelist's scorecard completes the round.
        Map<String, Object> scorecard = Map.of("rating", 4, "recommendation", "HIRE", "strengths", "Solid systems design");
        body(put("/api/v1/recruitment/interviews/{id}/feedback", interviewId), MANAGER, scorecard, 409);
        String fb = body(put("/api/v1/recruitment/interviews/{id}/feedback", interviewId), EMPLOYEE, scorecard, 200);
        assertThat(JsonPath.<String>read(fb, "$.interviewStatus")).isEqualTo("COMPLETED");

        // ---- 6. Offer: draft -> letter -> approval (hiring manager, HR) -> sent -> accepted.
        LocalDate joining = LocalDate.now().plusDays(21);
        String offer = body(post("/api/v1/recruitment/applications/{id}/offers", appId), RECRUITER, Map.of(
                "designationId", se, "departmentId", eng, "locationId", blr, "employmentType", "FULL_TIME",
                "annualCtc", 2000000, "joiningDate", joining.toString(),
                "expiryDate", LocalDate.now().plusDays(7).toString()), 201);
        long offerId = ((Number) JsonPath.read(offer, "$.id")).longValue();
        // Only one live offer per application.
        body(post("/api/v1/recruitment/applications/{id}/offers", appId), RECRUITER, Map.of(
                "designationId", se, "departmentId", eng, "locationId", blr, "employmentType", "FULL_TIME",
                "annualCtc", 1, "joiningDate", joining.toString()), 409);

        byte[] letter = mvc.perform(as(get("/api/v1/recruitment/offers/{id}/letter", offerId), RECRUITER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(letter, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        body(post("/api/v1/recruitment/offers/{id}/send", offerId), RECRUITER, null, 409);   // not approved yet
        String offerSubmitted = body(post("/api/v1/recruitment/offers/{id}/submit", offerId), RECRUITER, null, 200);
        long offerApproval = ((Number) JsonPath.read(offerSubmitted, "$.approvalRequestId")).longValue();
        mvc.perform(as(get("/api/v1/approvals/inbox"), MANAGER))
                .andExpect(jsonPath("$[?(@.subjectType == 'OFFER')]", not(empty())));
        assertThat(approveWholeChain(offerApproval)).containsExactly(MANAGER, HR);
        body(post("/api/v1/recruitment/offers/{id}/send", offerId), RECRUITER, null, 200);
        String accepted = body(post("/api/v1/recruitment/offers/{id}/response", offerId), RECRUITER,
                Map.of("accepted", true), 200);
        assertThat(JsonPath.<String>read(accepted, "$.status")).isEqualTo("ACCEPTED");

        // ---- 7. Convert to employee: the recruiter lacks EMPLOYEE_WRITE; HR can.
        Map<String, Object> hire = Map.of("workEmail", "zara.khan@hrgenius.com");
        body(post("/api/v1/recruitment/offers/{id}/hire", offerId), RECRUITER, hire, 403);
        String hired = body(post("/api/v1/recruitment/offers/{id}/hire", offerId), HR, hire, 201);
        long newEmpId = ((Number) JsonPath.read(hired, "$.employeeId")).longValue();
        assertThat(JsonPath.<String>read(hired, "$.loginEmail")).isEqualTo("zara.khan@hrgenius.com");
        assertThat(JsonPath.<String>read(hired, "$.temporaryPassword")).isNotBlank();
        long planId = ((Number) JsonPath.read(hired, "$.onboardingPlanId")).longValue();
        // Cannot hire twice.
        body(post("/api/v1/recruitment/offers/{id}/hire", offerId), HR, hire, 409);

        Map<String, Object> emp = jdbc.queryForMap(
                "SELECT manager_id, annual_ctc, status, date_of_joining FROM employees WHERE id = ?", newEmpId);
        assertThat(((Number) emp.get("manager_id")).longValue()).isEqualTo(managerId);
        assertThat(((Number) emp.get("annual_ctc")).longValue()).isEqualTo(2000000L);
        assertThat(emp.get("status")).isEqualTo("PROBATION");

        String detail = body(get("/api/v1/recruitment/applications/{id}", appId), RECRUITER, null, 200);
        assertThat(JsonPath.<String>read(detail, "$.stage")).isEqualTo("HIRED");
        assertThat(JsonPath.<String>read(detail, "$.requisition.status")).isEqualTo("CLOSED");   // 1 of 1 filled
        assertThat(JsonPath.<Integer>read(detail, "$.requisition.filled")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(detail, "$.events[*].eventType")).contains("HIRED", "FEEDBACK", "OFFER");

        // ---- 8. Onboarding plan from the default template, tasks routed to the right people.
        String plan = body(get("/api/v1/onboarding/plans/{id}", planId), HR, null, 200);
        assertThat(JsonPath.<List<Object>>read(plan, "$.tasks")).hasSize(11);
        assertThat(JsonPath.<List<Integer>>read(plan, "$.tasks[?(@.ownerRole == 'EMPLOYEE')].assigneeId"))
                .allMatch(v -> v.longValue() == newEmpId);
        assertThat(JsonPath.<List<Integer>>read(plan, "$.tasks[?(@.ownerRole == 'MANAGER')].assigneeId"))
                .allMatch(v -> v.longValue() == managerId);
        assertThat(JsonPath.<List<Object>>read(plan, "$.tasks[?(@.ownerRole == 'IT')].assigneeId"))
                .allMatch(v -> v == null);

        // The manager sees their tasks; someone else cannot tick them off; the manager can.
        String managerTasks = body(get("/api/v1/onboarding/me/tasks"), MANAGER, null, 200);
        List<Integer> managerTaskIds = JsonPath.read(managerTasks, "$[?(@.planId == " + planId + ")].id");
        assertThat(managerTaskIds).hasSize(2);
        long taskId = managerTaskIds.get(0);
        body(post("/api/v1/onboarding/tasks/{id}/status", taskId), EMPLOYEE, Map.of("status", "DONE"), 403);
        String done = body(post("/api/v1/onboarding/tasks/{id}/status", taskId), MANAGER, Map.of("status", "DONE"), 200);
        assertThat(JsonPath.<String>read(done, "$.completedBy")).isEqualTo("Manuel Garcia");

        // HR sees the plan in the list with progress; employees without ONBOARDING_MANAGE don't get the list.
        String plans = body(get("/api/v1/onboarding/plans"), HR, null, 200);
        assertThat(JsonPath.<List<Integer>>read(plans, "$[?(@.id == " + planId + ")].doneTasks")).containsExactly(1);
        mvc.perform(as(get("/api/v1/onboarding/plans"), MANAGER)).andExpect(status().isForbidden());
    }

    @Test
    void recruitmentApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/recruitment/requisitions")).andExpect(status().is(anyOf(is(401), is(403))));
        mvc.perform(get("/api/v1/onboarding/plans")).andExpect(status().is(anyOf(is(401), is(403))));
        // ...while the careers listing is public and only exposes published open roles.
        String jobs = mvc.perform(get("/api/v1/public/careers/jobs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(jobs, "$[*].title"))
                .contains("Senior Software Engineer (Backend)")
                .doesNotContain("DevOps Engineer");    // seeded as an unpublished draft
    }

    @Test
    void seededPanelistsSeeTheirPendingInterviews() throws Exception {
        String mine = body(get("/api/v1/recruitment/interviews/mine"), MANAGER, null, 200);
        assertThat(JsonPath.<List<String>>read(mine, "$[*].candidateName"))
                .contains("Arjun Mehta", "Meera Pillai", "Isha Kulkarni");
        assertThat(JsonPath.<List<Boolean>>read(mine, "$[?(@.candidateName == 'Meera Pillai')].submitted"))
                .containsExactly(true);
    }
}
