package com.hrgenius.common.error;

/** Thrown for domain/business-rule violations (maps to HTTP 409 by default). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
