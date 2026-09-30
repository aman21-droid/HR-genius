package com.hrgenius.auth.service;

import com.hrgenius.auth.dto.AuthDtos.*;
import com.hrgenius.auth.entity.RefreshToken;
import com.hrgenius.auth.entity.Role;
import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.RefreshTokenRepository;
import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Handles login (with lockout after repeated failures), refresh-token rotation, and logout.
 */
@Slf4j
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${hrgenius.security.access-token-ttl-ms}") long accessTokenTtlMs,
                       @Value("${hrgenius.security.refresh-token-ttl-ms}") long refreshTokenTtlMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.isLocked()) {
            throw new BusinessException("Account locked due to failed attempts. Try again later.");
        }
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("Account is disabled. Contact your administrator.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        // success: reset counters
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));
        if (!existing.isActive()) {
            throw new BusinessException("Refresh token expired or revoked. Please log in again.");
        }
        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new BusinessException("User no longer exists"));

        // rotation: revoke old, issue new
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException("User no longer exists"));
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(),
                user.getEmployeeId(), roles, permissions);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken()).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
            log.warn("User {} locked after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);
    }

    private TokenResponse issueTokens(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .toList();

        List<String> authorities = new java.util.ArrayList<>();
        roles.forEach(r -> authorities.add("ROLE_" + r));
        authorities.addAll(permissions);

        String accessToken = jwtService.generateAccessToken(user.getEmail(), authorities);
        String refreshToken = persistRefreshToken(user);

        UserSummary summary = new UserSummary(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getEmployeeId(), roles, permissions);

        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenTtlMs, summary);
    }

    private String persistRefreshToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID() + "." + UUID.randomUUID());
        token.setUserId(user.getId());
        token.setExpiresAt(Instant.now().plusMillis(refreshTokenTtlMs));
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return token.getToken();
    }
}
