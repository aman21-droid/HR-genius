package com.hrgenius.common.error;

/** Thrown for invalid client input not covered by Bean Validation (maps to HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
