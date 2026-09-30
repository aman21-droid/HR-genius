package com.hrgenius.common.error;

/** Thrown when a client exceeds a rate limit (maps to HTTP 429). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
