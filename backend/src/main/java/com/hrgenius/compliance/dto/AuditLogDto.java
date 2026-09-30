package com.hrgenius.compliance.dto;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        String entity,
        String entityId,
        String action,
        String field,
        String oldValue,
        String newValue,
        String actor,
        Instant changedAt) {
}
