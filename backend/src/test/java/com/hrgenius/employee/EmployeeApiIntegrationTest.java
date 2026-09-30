package com.hrgenius.employee;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.RoleRepository;
import com.hrgenius.auth.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests over the seeded demo data (H2 in Oracle mode, real migrations).
 * Uses its own in-memory database so its writes cannot affect other test classes.
 * Each test creates the data it mutates, so tests are independent of execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrgenius_api;MODE=Oracle;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
        "hrgenius.storage.root=target/test-storage-api"})
class EmployeeApiIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    // ------------------------------------------------------------------ helpers

    private String token(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String token(String email) throws Exception {
        return token(email, PASSWORD);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        return call(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), token);
    }

    private long id(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private long employeeId(String email) {
        return id("SELECT id FROM employees WHERE work_email = ?", email);
    }

    private long masterId(String table, String code) {
        return id("SELECT id FROM " + table + " WHERE code = ?", code);
    }

    private Map<String, Object> newEmployee(String email) {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "Test");
        m.put("lastName", "Person");
        m.put("workEmail", email);
        m.put("departmentId", masterId("departments", "ENG"));
        m.put("designationId", masterId("designations", "SE"));
        m.put("gradeId", masterId("grades", "G2"));
        m.put("locationId", masterId("locations", "BLR"));
        m.put("managerId", employeeId("manager@hrgenius.com"));
        m.put("employmentType", "FULL_TIME");
        m.put("dateOfJoining", "2026-01-05");
        m.put("dateOfBirth", "1996-03-10");
        m.put("annualCtc", 1200000);
        return m;
    }

    /** Turns a GET detail response into an update request body. */
    private Map<String, Object> requestFromDetail(String detailJson) throws Exception {
        Map<String, Object> d = json.readValue(detailJson, new TypeReference<>() { });
        Map<String, Object> r = new HashMap<>();
        for (String k : List.of("firstName", "middleName", "lastName", "workEmail", "personalEmail", "phone",
                "gender", "dateOfBirth", "maritalStatus", "bloodGroup", "nationality", "currentAddress",
                "permanentAddress", "departmentId", "designationId", "gradeId", "locationId", "managerId",
                "employmentType", "status", "dateOfJoining", "probationEndDate", "noticePeriodDays", "annualCtc")) {
            r.put(k, d.get(k));
        }
        return r;
    }

    private long createEmployee(String hrToken, String email) throws Exception {
        String body = send(post("/api/v1/employees"), hrToken, newEmployee(email))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.employee.id")).longValue();
    }

    // ------------------------------------------------------------------ visibility

    @Test
    void employeeSeesDirectoryButNotOthersPersonalData() throws Exception {
        String t = token("employee@hrgenius.com");
        long ceo = employeeId("arjun.mehta@hrgenius.com");
        long self = employeeId("employee@hrgenius.com");

        call(get("/api/v1/employees").param("size", "100"), t)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].workEmail", hasItem("arjun.mehta@hrgenius.com")))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("EXITED"))));

        call(get("/api/v1/employees/" + ceo), t)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitedView").value(true))
                .andExpect(jsonPath("$.dateOfBirth").value(nullValue()))
                .andExpect(jsonPath("$.annualCtc").value(nullValue()));

        call(get("/api/v1/employees/" + self), t)
                .andExpect(jsonPath("$.limitedView").value(false))
                .andExpect(jsonPath("$.dateOfBirth").value(notNullValue()))
                .andExpect(jsonPath("$.annualCtc").value(notNullValue()));

        call(get("/api/v1/employees/" + ceo + "/timeline"), t).andExpect(status().isForbidden());
        call(get("/api/v1/employees").param("status", "EXITED"), t)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void managerSeesTheirTeamButNotSensitiveData() throws Exception {
        String t = token("manager@hrgenius.com");
        long report = employeeId("employee@hrgenius.com");

        call(get("/api/v1/employees").param("teamOnly", "true").param("size", "100"), t)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].workEmail", hasItem("employee@hrgenius.com")))
                .andExpect(jsonPath("$.content[*].workEmail", not(hasItem("arjun.mehta@hrgenius.com"))));

        call(get("/api/v1/employees/" + report + "/timeline"), t).andExpect(status().isOk());
        call(get("/api/v1/employees/" + report), t).andExpect(jsonPath("$.limitedView").value(false))
                .andExpect(jsonPath("$.annualCtc").value(nullValue()));
        call(get("/api/v1/employees/" + report + "/statutory"), t).andExpect(status().isForbidden());
    }

    @Test
    void regularEmployeeCannotCreateEmployees() throws Exception {
        send(post("/api/v1/employees"), token("employee@hrgenius.com"), newEmployee("nope@hrgenius.com"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void hrCreatesEmployeeWithLoginThatWorks() throws Exception {
        Map<String, Object> req = newEmployee("new.joiner@hrgenius.com");
        req.put("createLogin", true);
        String body = send(post("/api/v1/employees"), token("hr@hrgenius.com"), req)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.employeeCode", startsWith("EMP")))
                .andExpect(jsonPath("$.employee.status").value("PROBATION"))
                .andExpect(jsonPath("$.loginEmail").value("new.joiner@hrgenius.com"))
                .andReturn().getResponse().getContentAsString();
        String tempPassword = JsonPath.read(body, "$.temporaryPassword");
        assertThat(token("new.joiner@hrgenius.com", tempPassword)).isNotBlank();
    }

    @Test
    void departmentChangeWritesTimelineAndAudit() throws Exception {
        String hr = token("hr@hrgenius.com");
        long id = createEmployee(hr, "mover@hrgenius.com");

        String detail = call(get("/api/v1/employees/" + id), hr).andReturn().getResponse().getContentAsString();
        Map<String, Object> req = requestFromDetail(detail);
        req.put("departmentId", masterId("departments", "PRD"));
        send(put("/api/v1/employees/" + id), hr, req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Product & Design"));

        call(get("/api/v1/employees/" + id + "/timeline"), hr)
                .andExpect(jsonPath("$[*].eventType", hasItems("JOINED", "TRANSFERRED")));
        assertThat(id("SELECT COUNT(*) FROM audit_log WHERE entity = 'Employee' AND entity_id = ? AND field = 'department'",
                String.valueOf(id))).isEqualTo(1);
    }

    @Test
    void reportingLoopIsRejected() throws Exception {
        String hr = token("hr@hrgenius.com");
        long manager = employeeId("manager@hrgenius.com");
        String detail = call(get("/api/v1/employees/" + manager), hr).andReturn().getResponse().getContentAsString();
        Map<String, Object> req = requestFromDetail(detail);
        req.put("managerId", employeeId("employee@hrgenius.com"));   // Emma reports to Manuel
        send(put("/api/v1/employees/" + manager), hr, req)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("reports")));
    }

    /** Regression: editors who can't see pay receive annualCtc=null and must not wipe it on save. */
    @Test
    void editorWithoutSensitiveAccessDoesNotWipeSalary() throws Exception {
        User u = new User();
        u.setEmail("hr.manager@hrgenius.com");
        u.setFullName("HR Manager (test)");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setRoles(new HashSet<>(Set.of(roles.findByCode("HR_MANAGER").orElseThrow())));
        users.save(u);
        String hrm = token("hr.manager@hrgenius.com");

        long id = createEmployee(token("hr@hrgenius.com"), "salaried@hrgenius.com");
        String detail = call(get("/api/v1/employees/" + id), hrm)
                .andExpect(jsonPath("$.annualCtc").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> req = requestFromDetail(detail);
        req.put("phone", "+91 9000000000");
        send(put("/api/v1/employees/" + id), hrm, req).andExpect(status().isOk());

        BigDecimal ctc = jdbc.queryForObject("SELECT annual_ctc FROM employees WHERE id = ?", BigDecimal.class, id);
        assertThat(ctc).isEqualByComparingTo("1200000");
    }

    @Test
    void cannotDeleteEmployeeWithDirectReports() throws Exception {
        call(delete("/api/v1/employees/" + employeeId("manager@hrgenius.com")), token("admin@hrgenius.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("direct report")));
    }

    @Test
    void cannotDeleteMasterInUse() throws Exception {
        call(delete("/api/v1/org/departments/" + masterId("departments", "ENG")), token("hr@hrgenius.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("current employee")));
    }

    // ------------------------------------------------------------------ sensitive data

    @Test
    void statutoryIsMaskedAndRevealIsAudited() throws Exception {
        String hr = token("hr@hrgenius.com");
        long id = employeeId("employee@hrgenius.com");
        long auditsBefore = id("SELECT COUNT(*) FROM audit_log WHERE action = 'VIEW_SENSITIVE'");

        call(get("/api/v1/employees/" + id + "/statutory"), hr)
                .andExpect(jsonPath("$.masked").value(true))
                .andExpect(jsonPath("$.pan", startsWith("XXXXXX")));
        call(get("/api/v1/employees/" + id + "/statutory").param("reveal", "true"), hr)
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.pan", matchesPattern("[A-Z]{5}[0-9]{4}[A-Z]")));

        assertThat(id("SELECT COUNT(*) FROM audit_log WHERE action = 'VIEW_SENSITIVE'")).isEqualTo(auditsBefore + 1);
        // Employees can see their own.
        call(get("/api/v1/employees/" + id + "/statutory"), token("employee@hrgenius.com")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ documents

    @Test
    void employeeUploadsOwnDocumentButNotSomeoneElses() throws Exception {
        String t = token("employee@hrgenius.com");
        long self = employeeId("employee@hrgenius.com");
        MockMultipartFile pdf = new MockMultipartFile("file", "degree.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile fakePdf = new MockMultipartFile("file", "evil.pdf", "application/pdf",
                "<html><script>x</script>".getBytes(StandardCharsets.UTF_8));

        call(multipart("/api/v1/employees/" + self + "/documents").file(pdf)
                .param("category", "EDUCATION").param("title", "Degree certificate"), t)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(false));   // awaits HR verification

        call(multipart("/api/v1/employees/" + self + "/documents").file(fakePdf)
                .param("category", "OTHER").param("title", "Sneaky"), t)
                .andExpect(status().isBadRequest());

        call(multipart("/api/v1/employees/" + employeeId("arjun.mehta@hrgenius.com") + "/documents").file(pdf)
                .param("category", "OTHER").param("title", "Not mine"), t)
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ import

    private MockMultipartFile workbook(String[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Employees");
            String[] headers = {"First Name *", "Last Name *", "Work Email *", "Department Code *",
                    "Designation Code *", "Location Code *", "Employment Type *", "Date of Joining *",
                    "Manager (Code or Email)"};
            Row h = s.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importIsAllOrNothingAndSupportsInFileManagers() throws Exception {
        String hr = token("hr@hrgenius.com");

        MockMultipartFile bad = workbook(
                new String[]{"Lead", "One", "lead.one@hrgenius.com", "ENG", "EM", "BLR", "FULL_TIME", "2026-02-01", "EMP0007"},
                new String[]{"Dev", "Two", "dev.two@hrgenius.com", "NOPE", "SE", "BLR", "Full Time", "2026-02-01", ""});
        call(multipart("/api/v1/employees/import").file(bad).param("dryRun", "false"), hr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[0].column").value("Department Code"));
        assertThat(id("SELECT COUNT(*) FROM employees WHERE work_email = 'lead.one@hrgenius.com'")).isZero();

        MockMultipartFile good = workbook(
                new String[]{"Lead", "One", "lead.one@hrgenius.com", "ENG", "EM", "BLR", "FULL_TIME", "2026-02-01", "EMP0007"},
                new String[]{"Dev", "Two", "dev.two@hrgenius.com", "ENG", "SE", "BLR", "full-time", "2026-02-01",
                        "lead.one@hrgenius.com"});
        call(multipart("/api/v1/employees/import").file(good).param("dryRun", "false"), hr)
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.created").value(2));
        assertThat(id("SELECT manager_id FROM employees WHERE work_email = 'dev.two@hrgenius.com'"))
                .isEqualTo(employeeId("lead.one@hrgenius.com"));
    }

    @Test
    void orgChartListsCurrentEmployeesWithReportCounts() throws Exception {
        call(get("/api/v1/org-chart"), token("employee@hrgenius.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.employeeCode == 'EMP0001')].managerId", contains(nullValue())))
                .andExpect(jsonPath("$[?(@.employeeCode == 'EMP0001')].directReports", contains(5)));
    }
}
