package com.hrgenius.compliance.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request/response shapes for the policy library. */
public final class PolicyDtos {

    private PolicyDtos() {
    }

    /** A policy as an employee sees it, with their own acknowledgement state. */
    public record PolicyDto(Long id, String code, String title, String category, String summary, String body,
                            int versionNo, boolean requiresAck, String status, LocalDate effectiveDate,
                            Instant publishedAt, boolean acknowledged, Instant acknowledgedAt,
                            /* HR only; null for employees */ Integer acknowledgedCount, Integer employeeCount) {
    }

    public record PolicyRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,30}$", message = "use 2-30 capital letters, digits or -") String code,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 40) String category,
            @Size(max = 500) String summary,
            @NotBlank @Size(max = 4000) String body,
            Boolean requiresAck,
            LocalDate effectiveDate) {
    }

    public record PendingEmployeeDto(Long employeeId, String employeeCode, String employeeName, String department) {
    }

    public record ComplianceDto(Long policyId, String title, int versionNo, int employeeCount, int acknowledgedCount,
                                List<PendingEmployeeDto> pending) {
    }
}
