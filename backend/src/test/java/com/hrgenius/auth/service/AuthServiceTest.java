package com.hrgenius.auth.service;

import com.hrgenius.auth.dto.AuthDtos.LoginRequest;
import com.hrgenius.auth.dto.AuthDtos.TokenResponse;
import com.hrgenius.auth.entity.Role;
import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.RefreshTokenRepository;
import com.hrgenius.auth.repository.UserRepository;
import com.hrgenius.common.error.BusinessException;
import com.hrgenius.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository,
                passwordEncoder, jwtService, 900_000L, 604_800_000L);
    }

    private User activeUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("employee@hrgenius.com");
        u.setPasswordHash("hash");
        u.setStatus(User.UserStatus.ACTIVE);
        Role role = new Role("EMPLOYEE", "Employee");
        u.setRoles(Set.of(role));
        return u;
    }

    @Test
    void login_succeeds_withValidCredentials_andResetsFailedAttempts() {
        User u = activeUser();
        u.setFailedAttempts(2);
        when(userRepository.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Password@123", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(anyString(), any())).thenReturn("access.jwt");

        TokenResponse resp = authService.login(new LoginRequest(u.getEmail(), "Password@123"));

        assertThat(resp.accessToken()).isEqualTo("access.jwt");
        assertThat(resp.user().roles()).contains("EMPLOYEE");
        assertThat(u.getFailedAttempts()).isZero();
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void login_incrementsFailedAttempts_onWrongPassword() {
        User u = activeUser();
        when(userRepository.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(u.getEmail(), "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(u.getFailedAttempts()).isEqualTo(1);
        assertThat(u.isLocked()).isFalse();
    }

    @Test
    void login_locksAccount_afterFifthFailure() {
        User u = activeUser();
        u.setFailedAttempts(4); // this attempt makes 5
        when(userRepository.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(u.getEmail(), "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());
        assertThat(u.getFailedAttempts()).isEqualTo(5);
        assertThat(u.isLocked()).isTrue();
    }

    @Test
    void login_rejectsLockedAccount() {
        User u = activeUser();
        u.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        when(userRepository.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login(new LoginRequest(u.getEmail(), "Password@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
    }
}
