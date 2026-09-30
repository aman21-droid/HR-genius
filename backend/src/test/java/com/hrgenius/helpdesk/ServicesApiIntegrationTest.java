package com.hrgenius.helpdesk;

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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 7 end to end: helpdesk tickets, policy acknowledgements, analytics. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_services;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-services"})
class ServicesApiIntegrationTest {

    private static final String EMPLOYEE = "employee@hrgenius.com";
    private static final String MANAGER = "manager@hrgenius.com";
    private static final String HR = "hr@hrgenius.com";
    private static final String PAYROLL = "payroll@hrgenius.com";
    private static final String RECRUITER = "recruiter@hrgenius.com";

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

    @Test
    void ticketLifecycleAndVisibility() throws Exception {
        String created = call(post("/api/v1/helpdesk/tickets"), MANAGER, Map.of("category", "IT", "priority", "URGENT",
                "subject", "Laptop won't boot", "description", "Black screen after the update"), 201);
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        assertThat(JsonPath.<String>read(created, "$.ticketNo")).matches("HD-\\d{5}");
        assertThat(JsonPath.<String>read(created, "$.status")).isEqualTo("OPEN");

        // Other employees cannot see it; the queue is agents-only.
        call(get("/api/v1/helpdesk/tickets/{id}", id), EMPLOYEE, null, 403);
        call(get("/api/v1/helpdesk/tickets"), EMPLOYEE, null, 403);
        String queue = call(get("/api/v1/helpdesk/tickets").param("category", "IT"), HR, null, 200);
        assertThat(JsonPath.<List<String>>read(queue, "$.content[*].subject")).contains("Laptop won't boot");

        // Agent assigns to themselves and adds an internal note plus a public reply.
        long hrId = jdbc.queryForObject("SELECT employee_id FROM users WHERE email = ?", Long.class, HR);
        long recruiterId = jdbc.queryForObject("SELECT employee_id FROM users WHERE email = ?", Long.class, RECRUITER);
        call(patch("/api/v1/helpdesk/tickets/{id}", id), HR, Map.of("assigneeId", recruiterId), 400);   // not an agent
        call(patch("/api/v1/helpdesk/tickets/{id}", id), HR, Map.of("assigneeId", hrId), 200);
        call(post("/api/v1/helpdesk/tickets/{id}/comments", id), HR, Map.of("body", "Likely the BIOS update", "internal", true), 200);
        String replied = call(post("/api/v1/helpdesk/tickets/{id}/comments", id), HR, Map.of("body", "Please hold the power button for 30s"), 200);
        assertThat(JsonPath.<String>read(replied, "$.status")).isEqualTo("IN_PROGRESS");
        assertThat((Object) JsonPath.read(replied, "$.firstResponseAt")).isNotNull();
        call(post("/api/v1/helpdesk/tickets/{id}/comments", id), MANAGER, Map.of("body", "x", "internal", true), 403);

        // The requester never sees the internal note.
        String requesterView = call(get("/api/v1/helpdesk/tickets/{id}", id), MANAGER, null, 200);
        assertThat(JsonPath.<List<String>>read(requesterView, "$.comments[*].body"))
                .containsExactly("Please hold the power button for 30s");

        // Resolve needs a note; requester reopens by replying, then closes with a rating.
        call(patch("/api/v1/helpdesk/tickets/{id}", id), HR, Map.of("status", "RESOLVED"), 400);
        call(patch("/api/v1/helpdesk/tickets/{id}", id), HR, Map.of("status", "RESOLVED", "resolutionNote", "Power-cycled"), 200);
        String reopened = call(post("/api/v1/helpdesk/tickets/{id}/comments", id), MANAGER, Map.of("body", "It happened again"), 200);
        assertThat(JsonPath.<String>read(reopened, "$.status")).isEqualTo("IN_PROGRESS");
        call(patch("/api/v1/helpdesk/tickets/{id}", id), HR, Map.of("status", "RESOLVED", "resolutionNote", "Reimaged"), 200);
        String closed = call(post("/api/v1/helpdesk/tickets/{id}/close", id), MANAGER, Map.of("satisfaction", 4), 200);
        assertThat(JsonPath.<String>read(closed, "$.status")).isEqualTo("CLOSED");
        assertThat(JsonPath.<Integer>read(closed, "$.satisfaction")).isEqualTo(4);
        call(post("/api/v1/helpdesk/tickets/{id}/comments", id), MANAGER, Map.of("body", "late"), 409);

        String stats = call(get("/api/v1/helpdesk/stats"), PAYROLL, null, 200);
        assertThat(JsonPath.<Integer>read(stats, "$.byStatus.CLOSED")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void policiesAreVersionedAndTracked() throws Exception {
        // Emma was seeded without acknowledgements, so both ack-required policies are pending for her.
        String pending = call(get("/api/v1/policies/pending"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<String>>read(pending, "$[*].code")).containsExactlyInAnyOrder("CODE-OF-CONDUCT", "IT-ACCEPTABLE-USE");
        // Employees only see published policies.
        String visible = call(get("/api/v1/policies"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<String>>read(visible, "$[*].status")).allMatch("PUBLISHED"::equals);

        long coc = jdbc.queryForObject("SELECT id FROM policies WHERE code = 'CODE-OF-CONDUCT'", Long.class);
        String acked = call(post("/api/v1/policies/{id}/acknowledge", coc), EMPLOYEE, null, 200);
        assertThat(JsonPath.<Boolean>read(acked, "$.acknowledged")).isTrue();
        call(post("/api/v1/policies/{id}/acknowledge", coc), EMPLOYEE, null, 200);          // idempotent
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/policies/pending"), EMPLOYEE, null, 200), "$[*].code"))
                .containsExactly("IT-ACCEPTABLE-USE");

        String before = call(get("/api/v1/policies/{id}/compliance", coc), HR, null, 200);
        int ackedBefore = JsonPath.read(before, "$.acknowledgedCount");
        assertThat(ackedBefore).isPositive();
        call(get("/api/v1/policies/{id}/compliance", coc), EMPLOYEE, null, 403);

        // Editing the wording of a published policy creates v3: everyone has to acknowledge again.
        Map<String, Object> edit = new HashMap<>(Map.of("code", "CODE-OF-CONDUCT", "title", "Code of Conduct",
                "category", "Ethics", "body", "Updated wording for 2027.", "requiresAck", true));
        String v3 = call(put("/api/v1/policies/{id}", coc), HR, edit, 200);
        assertThat(JsonPath.<Integer>read(v3, "$.versionNo")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(call(get("/api/v1/policies/{id}/compliance", coc), HR, null, 200), "$.acknowledgedCount")).isZero();
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/policies/pending"), EMPLOYEE, null, 200), "$[*].code"))
                .contains("CODE-OF-CONDUCT");

        // Drafts publish; only POLICY_MANAGE may author.
        call(post("/api/v1/policies"), MANAGER, Map.of("code", "X-1", "title", "x", "category", "x", "body", "x"), 403);
        String draft = call(post("/api/v1/policies"), HR, Map.of("code", "TRAVEL", "title", "Travel Policy",
                "category", "Finance", "body", "Book economy for trips under 6 hours."), 201);
        long travel = ((Number) JsonPath.read(draft, "$.id")).longValue();
        assertThat(JsonPath.<String>read(call(post("/api/v1/policies/{id}/publish", travel), HR, null, 200), "$.status"))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void analyticsOverviewIsConsistentAndRestricted() throws Exception {
        call(get("/api/v1/analytics/overview"), EMPLOYEE, null, 403);
        call(get("/api/v1/analytics/overview"), MANAGER, null, 403);
        String o = call(get("/api/v1/analytics/overview"), HR, null, 200);

        int headcount = JsonPath.read(o, "$.kpis.headcount");
        int current = jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE deleted = 0 AND status <> 'EXITED' "
                + "AND date_of_joining <= CURRENT_DATE", Integer.class);
        assertThat(headcount).isEqualTo(current);
        List<Integer> byDept = JsonPath.read(o, "$.byDepartment[*].value");
        assertThat(byDept.stream().mapToInt(Integer::intValue).sum()).isEqualTo(headcount);
        List<Integer> tenure = JsonPath.read(o, "$.tenure[*].value");
        assertThat(tenure.stream().mapToInt(Integer::intValue).sum()).isEqualTo(headcount);
        assertThat(JsonPath.<List<Object>>read(o, "$.movement")).hasSize(12);
        assertThat(JsonPath.<List<String>>read(o, "$.payrollTrend[*].period")).contains("2026-08", "2026-09");
        assertThat(JsonPath.<String>read(o, "$.ratingCycle")).isEqualTo("H1 2026");
        assertThat(JsonPath.<Integer>read(o, "$.kpis.openRequisitions")).isEqualTo(3);

        // Payroll admins hold ANALYTICS_VIEW too; the export is a real workbook.
        byte[] xlsx = mvc.perform(get("/api/v1/analytics/headcount.xlsx").header("Authorization", bearer(PAYROLL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
    }
}
