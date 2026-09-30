package com.hrgenius.performance;

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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6 end to end: goals, self and manager assessments, visibility rules, cycles, feedback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_perf;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-perf"})
class PerformanceApiIntegrationTest {

    private static final String EMPLOYEE = "employee@hrgenius.com";   // Emma, reports to Manuel
    private static final String MANAGER = "manager@hrgenius.com";     // Manuel
    private static final String HR = "hr@hrgenius.com";
    private static final String PAYROLL = "payroll@hrgenius.com";

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

    private long num(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static Map<String, Object> ratings(List<Integer> goalIds, int... values) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < goalIds.size(); i++) {
            list.add(Map.of("goalId", goalIds.get(i), "rating", values[i], "comment", "c" + i));
        }
        Map<String, Object> m = new HashMap<>();
        m.put("goals", list);
        return m;
    }

    @Test
    void reviewFlowFromGoalsToAcknowledgement() throws Exception {
        String mine = call(get("/api/v1/performance/me/reviews"), EMPLOYEE, null, 200);
        assertThat(JsonPath.<List<String>>read(mine, "$[*].cycleName")).containsExactly("H2 2026", "H1 2026");
        long reviewId = num(mine, "$[0].id");

        String review = call(get("/api/v1/performance/reviews/{id}", reviewId), EMPLOYEE, null, 200);
        assertThat(JsonPath.<String>read(review, "$.viewerRole")).isEqualTo("SELF");
        assertThat(JsonPath.<Integer>read(review, "$.totalWeight")).isEqualTo(100);
        List<Integer> goalIds = JsonPath.read(review, "$.goals[*].id");
        assertThat(goalIds).hasSize(3);

        // The manager sees it in their team list; an unrelated employee may not open it.
        String team = call(get("/api/v1/performance/team/reviews"), MANAGER, null, 200);
        assertThat(JsonPath.<List<Integer>>read(team, "$[*].id")).contains((int) reviewId);
        call(get("/api/v1/performance/reviews/{id}", reviewId), PAYROLL, null, 403);

        // Progress updates; an extra goal breaks the 100% rule until removed.
        call(patch("/api/v1/performance/reviews/{id}/goals/{g}/progress", reviewId, goalIds.get(1)), EMPLOYEE,
                Map.of("progress", 55, "status", "ON_TRACK"), 200);
        String withExtra = call(post("/api/v1/performance/reviews/{id}/goals", reviewId), EMPLOYEE,
                Map.of("title", "Write a tech blog post", "weight", 10), 200);
        assertThat(JsonPath.<Integer>read(withExtra, "$.totalWeight")).isEqualTo(110);
        Map<String, Object> self = ratings(goalIds, 4, 3, 5);
        self.put("overallRating", 4);
        self.put("comments", "Good half");
        self.put("submit", true);
        String err = call(put("/api/v1/performance/reviews/{id}/self", reviewId), EMPLOYEE, self, 409);
        assertThat(JsonPath.<String>read(err, "$.message")).contains("100%");
        long extraId = ((Number) JsonPath.<List<Integer>>read(withExtra, "$.goals[?(@.title == 'Write a tech blog post')].id").get(0)).longValue();
        call(delete("/api/v1/performance/reviews/{id}/goals/{g}", reviewId, extraId), EMPLOYEE, null, 200);

        // The manager cannot write before the self-assessment, and cannot see a draft of it.
        Map<String, Object> early = ratings(goalIds, 5, 4, 3);
        early.put("overallRating", 4);
        call(put("/api/v1/performance/reviews/{id}/manager", reviewId), MANAGER, early, 409);
        Map<String, Object> draft = ratings(goalIds, 4, 3, 5);
        draft.put("overallRating", 4);
        draft.put("submit", false);
        call(put("/api/v1/performance/reviews/{id}/self", reviewId), EMPLOYEE, draft, 200);
        String managerView = call(get("/api/v1/performance/reviews/{id}", reviewId), MANAGER, null, 200);
        assertThat((Object) JsonPath.read(managerView, "$.selfRating")).isNull();

        String submitted = call(put("/api/v1/performance/reviews/{id}/self", reviewId), EMPLOYEE, self, 200);
        assertThat(JsonPath.<String>read(submitted, "$.status")).isEqualTo("SELF_SUBMITTED");
        call(post("/api/v1/performance/reviews/{id}/goals", reviewId), EMPLOYEE, Map.of("title", "Late", "weight", 0), 409);

        // Manager drafts, which Emma cannot see; then submits: 50*5 + 30*4 + 20*3 = 430 -> 4.30.
        Map<String, Object> mgr = ratings(goalIds, 5, 4, 3);
        mgr.put("overallRating", 4);
        mgr.put("comments", "Strong delivery");
        mgr.put("submit", false);
        call(put("/api/v1/performance/reviews/{id}/manager", reviewId), MANAGER, mgr, 200);
        String emmaView = call(get("/api/v1/performance/reviews/{id}", reviewId), EMPLOYEE, null, 200);
        assertThat((Object) JsonPath.read(emmaView, "$.managerRating")).isNull();
        assertThat((Object) JsonPath.read(emmaView, "$.goals[0].managerRating")).isNull();
        mgr.put("submit", true);
        String done = call(put("/api/v1/performance/reviews/{id}/manager", reviewId), MANAGER, mgr, 200);
        assertThat(JsonPath.<Double>read(done, "$.finalScore")).isEqualTo(4.30);

        emmaView = call(get("/api/v1/performance/reviews/{id}", reviewId), EMPLOYEE, null, 200);
        assertThat(JsonPath.<Integer>read(emmaView, "$.managerRating")).isEqualTo(4);
        assertThat(JsonPath.<Boolean>read(emmaView, "$.canAcknowledge")).isTrue();
        call(post("/api/v1/performance/reviews/{id}/acknowledge", reviewId), MANAGER, Map.of(), 403);
        String ack = call(post("/api/v1/performance/reviews/{id}/acknowledge", reviewId), EMPLOYEE,
                Map.of("comment", "Thanks!"), 200);
        assertThat(JsonPath.<String>read(ack, "$.status")).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void hrRunsCyclesAndSeesDistribution() throws Exception {
        call(get("/api/v1/performance/cycles"), MANAGER, null, 403);       // line managers are not cycle admins
        String cycles = call(get("/api/v1/performance/cycles"), HR, null, 200);
        long h1 = ((Number) JsonPath.<List<Integer>>read(cycles, "$[?(@.name == 'H1 2026')].id").get(0)).longValue();
        String summary = call(get("/api/v1/performance/cycles/{id}/summary", h1), HR, null, 200);
        int reviewCount = JsonPath.read(summary, "$.cycle.reviewCount");
        Map<String, Integer> dist = JsonPath.read(summary, "$.ratingDistribution");
        assertThat(dist.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(reviewCount);
        assertThat(JsonPath.<Integer>read(summary, "$.byStatus.ACKNOWLEDGED")).isEqualTo(reviewCount);

        String created = call(post("/api/v1/performance/cycles"), HR, Map.of("name", "FY27 Q1",
                "startDate", "2027-01-01", "endDate", "2027-03-31"), 201);
        long cycleId = num(created, "$.id");
        String launched = call(post("/api/v1/performance/cycles/{id}/launch", cycleId), HR, null, 200);
        assertThat(JsonPath.<String>read(launched, "$.status")).isEqualTo("ACTIVE");
        int expected = jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE deleted = 0 AND manager_id IS NOT NULL "
                + "AND status <> 'EXITED' AND (exit_date IS NULL OR exit_date > DATE '2027-03-31') "
                + "AND date_of_joining <= DATE '2027-03-31'", Integer.class);
        assertThat(JsonPath.<Integer>read(launched, "$.reviewCount")).isEqualTo(expected);
        call(post("/api/v1/performance/cycles/{id}/launch", cycleId), HR, null, 409);
        assertThat(JsonPath.<String>read(call(post("/api/v1/performance/cycles/{id}/close", cycleId), HR, null, 200),
                "$.status")).isEqualTo("CLOSED");
    }

    @Test
    void feedbackVisibilityRules() throws Exception {
        long manuel = jdbc.queryForObject("SELECT id FROM employees WHERE work_email = ?", Long.class, MANAGER);
        long emma = jdbc.queryForObject("SELECT id FROM employees WHERE work_email = ?", Long.class, EMPLOYEE);

        call(post("/api/v1/performance/feedback"), EMPLOYEE, Map.of("toEmployeeId", manuel, "kind", "PRAISE",
                "visibility", "PUBLIC", "message", "Great 1:1s this quarter"), 201);
        String wall = call(get("/api/v1/performance/feedback/wall"), PAYROLL, null, 200);
        assertThat(JsonPath.<List<String>>read(wall, "$[*].message")).contains("Great 1:1s this quarter");

        call(post("/api/v1/performance/feedback"), EMPLOYEE, Map.of("toEmployeeId", manuel, "kind", "CONSTRUCTIVE",
                "visibility", "PUBLIC", "message", "no"), 400);
        call(post("/api/v1/performance/feedback"), EMPLOYEE, Map.of("toEmployeeId", emma, "kind", "PRAISE",
                "visibility", "PUBLIC", "message", "me"), 400);

        // Private note to Emma: she sees it, her manager sees it, it never reaches the wall.
        call(post("/api/v1/performance/feedback"), HR, Map.of("toEmployeeId", emma, "kind", "CONSTRUCTIVE",
                "visibility", "PRIVATE", "message", "Please update your timesheets on time"), 201);
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/performance/feedback/received"), EMPLOYEE, null, 200), "$[*].message"))
                .contains("Please update your timesheets on time");
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/performance/feedback/team"), MANAGER, null, 200), "$[*].message"))
                .contains("Please update your timesheets on time");
        assertThat(JsonPath.<List<String>>read(call(get("/api/v1/performance/feedback/wall"), EMPLOYEE, null, 200), "$[*].message"))
                .doesNotContain("Please update your timesheets on time");
    }
}
