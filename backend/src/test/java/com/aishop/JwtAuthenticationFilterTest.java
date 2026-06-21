package com.aishop;

import com.aishop.common.security.CurrentUser;
import com.aishop.common.security.jwt.JwtAuthenticationFilter;
import com.aishop.common.security.jwt.JwtTokenProvider;
import com.aishop.infrastructure.persistence.entity.UserEntity;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledUserCannotUsePreviouslyIssuedAccessToken() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserEntity user = user("DISABLED", "CUSTOMER");
        stubValidToken(user);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"", "账号已被禁用");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void activeUserUsesCurrentDatabaseRole() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserEntity user = user("ACTIVE", "MERCHANT");
        stubValidToken(user);

        filter.doFilter(request, response, filterChain);

        CurrentUser principal = (CurrentUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.userId()).isEqualTo("u10001");
        assertThat(principal.role()).isEqualTo("MERCHANT");
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer access-token");
        return request;
    }

    private void stubValidToken(UserEntity user) {
        when(jwtTokenProvider.validateAccessToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromAccessToken("access-token")).thenReturn(user.getUserId());
        when(userRepository.findByUserId(user.getUserId())).thenReturn(Optional.of(user));
    }

    private UserEntity user(String status, String role) {
        UserEntity user = new UserEntity();
        user.setUserId("u10001");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
