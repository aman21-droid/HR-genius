package com.hrgenius.auth.controller;

import com.hrgenius.auth.dto.AuthDtos.*;
import com.hrgenius.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints: login, refresh-token rotation, and logout. All are public;
 * everything else in the API requires the issued Bearer access token.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in with email and password; returns access + refresh tokens")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Exchange a valid refresh token for a new access + refresh token pair")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "Return the currently authenticated user (used to restore the SPA session)")
    @GetMapping("/me")
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.currentUser(email));
    }

    @Operation(summary = "Change the signed-in user's password (signs out other devices)")
    @PostMapping("/change-password")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal String email,
                                                        @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(email, request));
    }

    @Operation(summary = "Revoke a refresh token (logout)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
