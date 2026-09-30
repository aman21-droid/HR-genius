package com.hrgenius.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Request/response payloads for the auth endpoints, grouped for brevity. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record LogoutRequest(
            @NotBlank String refreshToken) {
    }

    /** Tokens plus a light user summary the SPA uses to render the shell without a second call. */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInMs,
            UserSummary user) {
    }

    public record UserSummary(
            Long id,
            String email,
            String fullName,
            Long employeeId,
            List<String> roles,
            List<String> permissions) {
    }
}
