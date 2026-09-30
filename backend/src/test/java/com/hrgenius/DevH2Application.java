package com.hrgenius;

/**
 * Runs the real application against an in-memory H2 database (Oracle mode) with all Flyway
 * migrations and demo seed data, for a quick local demo without Docker/Oracle:
 *
 * <pre>./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.hrgenius.DevH2Application</pre>
 *
 * Lives in test sources because H2 is a test-scoped dependency. Data resets on every restart.
 */
public class DevH2Application {

    public static void main(String[] args) {
        // Test classpath (H2) + "test" profile = in-memory Oracle-mode database with seed data.
        System.setProperty("spring.profiles.active", "test");
        HrGeniusApplication.main(args);
    }
}
