package com.hrgenius;

import com.hrgenius.employee.entity.EmployeeStatutory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full context against H2 (Oracle mode), which runs every Flyway migration
 * including the Java seed, then checks the seed landed and encryption round-trips.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationSmokeTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void seedDataIsPresent() {
        assertThat(count("SELECT COUNT(*) FROM employees")).isEqualTo(52);
        assertThat(count("SELECT COUNT(*) FROM employees WHERE status = 'EXITED'")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM departments")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM locations")).isEqualTo(3);
        // Everyone except the CEO has a manager.
        assertThat(count("SELECT COUNT(*) FROM employees WHERE manager_id IS NULL")).isEqualTo(1);
        // All six demo logins are linked to employee records.
        assertThat(count("SELECT COUNT(*) FROM users WHERE employee_id IS NOT NULL")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM employee_statutory")).isEqualTo(52);
        assertThat(count("SELECT COUNT(*) FROM employee_documents")).isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM assets WHERE status = 'ASSIGNED'"))
                .isEqualTo(count("SELECT COUNT(*) FROM asset_assignments WHERE returned_on IS NULL"));
    }

    @Test
    void employeeCodesAreSequential() {
        String ceo = jdbc.queryForObject(
                "SELECT employee_code FROM employees WHERE work_email = 'arjun.mehta@hrgenius.com'", String.class);
        assertThat(ceo).isEqualTo("EMP0001");
    }

    @Test
    @Transactional
    void sensitiveIdsAreEncryptedAtRestAndDecryptThroughJpa() {
        String raw = jdbc.queryForObject("SELECT pan_enc FROM employee_statutory WHERE employee_id = 1", String.class);
        assertThat(raw).doesNotMatch("[A-Z]{5}[0-9]{4}[A-Z]");      // ciphertext, not a PAN

        EmployeeStatutory s = em.find(EmployeeStatutory.class, 1L);
        assertThat(s.getPan()).matches("[A-Z]{3}P[A-Z][0-9]{4}[A-Z]");
        assertThat(s.getAadhaar()).matches("[2-9][0-9]{11}");
    }

    @Test
    void phase3SeedIsPresent() {
        // Five leave types, all active.
        assertThat(count("SELECT COUNT(*) FROM leave_types")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM leave_types WHERE active = 1")).isEqualTo(5);

        // 2026 holiday calendar: 8 mandatory + 2 optional.
        assertThat(count("SELECT COUNT(*) FROM holidays WHERE year_no = 2026")).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM holidays WHERE optional_holiday = 1")).isEqualTo(2);

        // Opening balances: every current (non-exited) employee gets CL, SL, EL for 2026.
        int activeEmployees = count("SELECT COUNT(*) FROM employees WHERE deleted = 0 AND status <> 'EXITED'");
        assertThat(count("SELECT COUNT(*) FROM leave_balances WHERE year_no = 2026")).isEqualTo(activeEmployees * 3);
        // Earned Leave was seeded at its 15-day annual entitlement.
        assertThat(count("SELECT COUNT(*) FROM leave_balances b JOIN leave_types t ON t.id = b.leave_type_id "
                + "WHERE t.code = 'EL' AND b.accrued = 15")).isEqualTo(activeEmployees);
    }

    @Test
    void phase4SeedIsPresent() {
        // Three open requisitions plus one draft, with sequential codes.
        assertThat(count("SELECT COUNT(*) FROM job_requisitions WHERE status = 'OPEN'")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM job_requisitions WHERE status = 'DRAFT'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM job_requisitions WHERE req_code = 'REQ-0001'")).isEqualTo(1);

        // Nine candidates, each with exactly one application.
        assertThat(count("SELECT COUNT(*) FROM candidates")).isEqualTo(9);
        assertThat(count("SELECT COUNT(*) FROM job_applications")).isEqualTo(9);
        // Every application has an "applied" event; the 6 not in APPLIED have a stage-move event too;
        // plus one INTERVIEW_SCHEDULED per interview (3).
        assertThat(count("SELECT COUNT(*) FROM application_events")).isEqualTo(9 + 6 + 3);

        // Panel seats: 2 on Arjun's round, 1 on Meera's (submitted), 1 on Isha's.
        assertThat(count("SELECT COUNT(*) FROM interview_feedback")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM interview_feedback WHERE submitted_at IS NOT NULL")).isEqualTo(1);

        // Default onboarding template with 11 tasks.
        assertThat(count("SELECT COUNT(*) FROM onboarding_templates WHERE is_default = 1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM onboarding_template_tasks")).isEqualTo(11);

        // HR_ADMIN received the two new permissions.
        assertThat(count("SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id "
                + "JOIN permissions p ON p.id = rp.permission_id "
                + "WHERE r.code = 'HR_ADMIN' AND p.code IN ('RECRUITMENT_APPROVE','ONBOARDING_MANAGE')")).isEqualTo(2);
    }
}
