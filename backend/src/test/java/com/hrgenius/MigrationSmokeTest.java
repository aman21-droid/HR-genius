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
}
