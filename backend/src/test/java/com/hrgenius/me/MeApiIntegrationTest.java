package com.hrgenius.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 8: the dashboard summary aggregates to-dos across modules; password change rotates sessions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_me;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-me"})
class MeApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String email, String password, int expected) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private String token(String email) throws Exception {
        return JsonPath.read(login(email, "Password@123", 200), "$.accessToken");
    }

    @Test
    void missingOrInvalidTokenIs401SoTheClientRefreshes() throws Exception {
        mvc.perform(get("/api/v1/me/summary")).andExpect(status().isUnauthorized());
        String body = mvc.perform(get("/api/v1/payroll/runs").header("Authorization", "Bearer expired.or.tampered"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.message")).contains("session has expired");
        // Signed in but not entitled stays 403.
        mvc.perform(get("/api/v1/payroll/runs").header("Authorization", "Bearer " + token("employee@hrgenius.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardBlocksFollowTheViewersRole() throws Exception {
        String hr = mvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + token("hr@hrgenius.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(hr, "$.hiring.openRequisitions")).isPositive();
        assertThat(JsonPath.<List<String>>read(hr, "$.hiring.pipeline[*].stage")).isNotEmpty();

        String manager = mvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + token("manager@hrgenius.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(manager, "$.hiring")).isNull();
        assertThat(JsonPath.<Integer>read(manager, "$.team.size")).isPositive();

        String employee = mvc.perform(get("/api/v1/me/dashboard").header("Authorization", "Bearer " + token("employee@hrgenius.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(employee, "$.hiring")).isNull();
        assertThat(JsonPath.<Object>read(employee, "$.team")).isNull();
    }

    @Test
    void summaryCollectsTodosFromEveryModule() throws Exception {
        String body = mvc.perform(get("/api/v1/me/summary").header("Authorization", "Bearer " + token("employee@hrgenius.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> kinds = JsonPath.read(body, "$.todos[*].kind");
        // Emma: two policies to acknowledge (seeded without acks), H2 self-assessment, a resolved ticket to confirm,
        // and an upcoming interview as a panelist.
        assertThat(kinds).contains("POLICY", "REVIEW", "TICKET", "INTERVIEW");
        assertThat(JsonPath.<String>read(body, "$.latestPayslip.period")).isEqualTo("2026-09");
        assertThat(JsonPath.<List<String>>read(body, "$.leaveBalances[*].code")).contains("CL", "SL", "EL");
        assertThat(JsonPath.<List<Object>>read(body, "$.kudos")).isNotEmpty();

        String manager = mvc.perform(get("/api/v1/me/summary").header("Authorization", "Bearer " + token("manager@hrgenius.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(manager, "$.todos[*].kind")).contains("INTERVIEW");
    }

    /** Regression: marking a contact primary clears the flag on the others (bulk update on a NUMBER(1) column). */
    @Test
    void onlyOneEmergencyContactIsPrimary() throws Exception {
        String t = token("employee@hrgenius.com");
        String me = mvc.perform(get("/api/v1/employees/me").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(me, "$.id")).longValue();
        for (String name : List.of("Asha Lopez", "Ravi Lopez")) {
            mvc.perform(post("/api/v1/employees/{id}/emergency-contacts", id).header("Authorization", "Bearer " + t)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("name", name, "relationship", "Sibling",
                                    "phone", "+91 9000000000", "primary", true))))
                    .andExpect(status().is2xxSuccessful());
        }
        String contacts = mvc.perform(get("/api/v1/employees/{id}/emergency-contacts", id).header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(contacts, "$[?(@.primary == true)].name")).containsExactly("Ravi Lopez");
    }

    @Test
    void changePasswordRequiresTheCurrentOneAndAStrongNewOne() throws Exception {
        String t = token("payroll@hrgenius.com");
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "wrong", "newPassword", "NewPass@2027"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "Password@123", "newPassword", "weakpass"))))
                .andExpect(status().isBadRequest());
        String res = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "Password@123", "newPassword", "NewPass2027"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(res, "$.refreshToken")).isNotBlank();

        login("payroll@hrgenius.com", "Password@123", 401);
        login("payroll@hrgenius.com", "NewPass2027", 200);
    }
}
