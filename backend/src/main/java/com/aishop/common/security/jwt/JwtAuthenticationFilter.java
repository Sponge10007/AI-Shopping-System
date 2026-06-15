package com.aishop.common.security.jwt;

import com.aishop.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 *
 * 从请求头 Authorization: Bearer <token> 中提取 JWT，
 * 验证后设置 SecurityContext。
 *
 * 白名单路径（不需要认证）：
 * - POST /api/v1/auth/register
 * - POST /api/v1/auth/login
 * - GET /api/v1/products (公开查询)
 * - GET /api/v1/products/{id}
 * - GET /api/v1/search/**
 * - GET /api/v1/recommendations/**
 * - GET /actuator/health
 * - /internal/v1/**
 *
 * 非白名单路径如果没有 Token，直接返回 401
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // 白名单路径跳过认证
        if (isPublicPath(requestURI, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 Token
        String token = extractToken(request);

        if (token == null) {
            log.debug("No JWT token found for protected request: {} {}", method, requestURI);
            // 非白名单路径无 Token 返回 401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录\"}");
            return;
        }

        // 验证 Token
        if (!jwtTokenProvider.validateAccessToken(token)) {
            log.debug("Invalid JWT token for request: {} {}", method, requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Token无效或已过期\"}");
            return;
        }

        // 解析用户信息并设置到 SecurityContext
        try {
            String userId = jwtTokenProvider.getUserIdFromAccessToken(token);
            String role = jwtTokenProvider.getRoleFromAccessToken(token);

            CurrentUser currentUser = new CurrentUser(userId, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("Failed to parse JWT token: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Token解析失败\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 判断是否为公开路径（不需要认证）
     */
    private boolean isPublicPath(String uri, String method) {
        // 注册和登录
        if (uri.equals("/api/v1/auth/register") && "POST".equals(method)) return true;
        if (uri.equals("/api/v1/auth/login") && "POST".equals(method)) return true;

        // 健康检查
        if (uri.equals("/actuator/health")) return true;

        // 内部接口（由 Internal-Token 头验证）
        if (uri.startsWith("/internal/v1/")) return true;

        // 公开的商品查询（GET 请求）
        if (uri.equals("/api/v1/products") && "GET".equals(method)) return true;
        if (uri.matches("/api/v1/products/\\w+") && "GET".equals(method)) return true;

        // 公开的搜索
        if (uri.startsWith("/api/v1/search/") && "GET".equals(method)) return true;

        // 公开的推荐
        if (uri.startsWith("/api/v1/recommendations/") && "GET".equals(method)) return true;

        return false;
    }
}
