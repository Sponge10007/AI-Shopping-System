package com.aishop.common.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token 生成与验证工具
 *
 * 职责：
 * 1. 生成 Access Token（短期，2小时）
 * 2. 生成 Refresh Token（长期，7天）
 * 3. 验证 Token 并解析出用户信息
 *
 * Token 载荷（Payload）包含：
 * - sub: userId（业务主键）
 * - role: 用户角色（CUSTOMER / MERCHANT）
 * - iat: 签发时间
 * - exp: 过期时间
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationSec() * 1000);

        SecretKey key = getAccessTokenSigningKey();

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(String userId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpirationSec() * 1000);

        SecretKey key = getRefreshTokenSigningKey();

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 从 Access Token 中提取 userId
     */
    public String getUserIdFromAccessToken(String token) {
        return parseClaims(token, getAccessTokenSigningKey()).getSubject();
    }

    /**
     * 从 Access Token 中提取 role
     */
    public String getRoleFromAccessToken(String token) {
        return parseClaims(token, getAccessTokenSigningKey()).get("role", String.class);
    }

    /**
     * 验证 Access Token 是否有效
     */
    public boolean validateAccessToken(String token) {
        return validateToken(token, getAccessTokenSigningKey());
    }

    /**
     * 验证 Refresh Token 是否有效
     */
    public boolean validateRefreshToken(String token) {
        return validateToken(token, getRefreshTokenSigningKey());
    }

    /**
     * 从 Refresh Token 中提取 userId
     */
    public String getUserIdFromRefreshToken(String token) {
        return parseClaims(token, getRefreshTokenSigningKey()).getSubject();
    }

    /**
     * 获取 Access Token 过期时间（秒）
     * 用于登录响应中告知前端
     */
    public long getAccessTokenExpirationSec() {
        return jwtProperties.getAccessTokenExpirationSec();
    }

    // ===== 私有方法 =====

    private SecretKey getAccessTokenSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(
                        jwtProperties.getAccessTokenSecret().getBytes()
                )
        );
        // 确保密钥至少 256 位
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getRefreshTokenSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(
                        jwtProperties.getRefreshTokenSecret().getBytes()
                )
        );
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean validateToken(String token, SecretKey key) {
        try {
            parseClaims(token, key);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }
}
