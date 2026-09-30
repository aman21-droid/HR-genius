package com.hrgenius;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HRGenius - Human Resource Management System.
 * Entry point. Scheduling (leave accrual, reminders) and caching (lookups) are enabled here.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class HrGeniusApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrGeniusApplication.class, args);
    }
}
