package com.aishop;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.security.jwt.JwtTokenProvider;
import com.aishop.infrastructure.persistence.entity.UserEntity;
import com.aishop.infrastructure.persistence.repository.UserProfileRepository;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.auth.AuthService;
import com.aishop.modules.auth.dto.LoginRequest;
import com.aishop.modules.auth.dto.LoginResponse;
import com.aishop.modules.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserService userService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                userProfileRepository,
                passwordEncoder,
                jwtTokenProvider,
                userService
        );
    }

    @Test
    void disabledUserCannotLogin() {
        UserEntity user = user("DISABLED");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "Password123!")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("禁用");
                });

        verify(passwordEncoder, never()).matches("Password123!", user.getPasswordHash());
        verify(jwtTokenProvider, never()).generateAccessToken(user.getUserId(), user.getRole());
    }

    @Test
    void activeUserCanLogin() {
        UserEntity user = user("ACTIVE");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", user.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken("u10001", "CUSTOMER")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken("u10001", "CUSTOMER")).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSec()).thenReturn(7200L);

        LoginResponse response = authService.login(new LoginRequest("alice", "Password123!"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().userId()).isEqualTo("u10001");
    }

    private UserEntity user(String status) {
        UserEntity user = new UserEntity();
        user.setUserId("u10001");
        user.setUsername("alice");
        user.setPhone("13800000000");
        user.setPasswordHash("password-hash");
        user.setRole("CUSTOMER");
        user.setStatus(status);
        return user;
    }
}
