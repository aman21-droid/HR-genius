package com.hrgenius.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error JSON returned by the global exception handler.
 * Example:
 * { "timestamp": "...", "status": 400, "error": "Bad Request",
 *   "message": "Validation failed", "path": "/api/v1/...", "fieldErrors": [...] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> fieldErrors
) {
    public record FieldValidationError(String field, String message) {
    }
}
