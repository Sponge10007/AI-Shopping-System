package com.aishop.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 *
 * 从 application.yml 的 app.jwt 前缀读取
 * 提供 Access Token 和 Refresh Token 的密钥与过期时间配置
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Access Token 密钥（至少 256 位，即 32 个字符）
     */
    private String accessTokenSecret = "aishop-access-token-secret-key-32chars!!";

    /**
     * Access Token 过期时间（秒），默认 2 小时
     */
    private long accessTokenExpirationSec = 7200;

    /**
     * Refresh Token 密钥
     */
    private String refreshTokenSecret = "aishop-refresh-token-secret-key-32chars!!";

    /**
     * Refresh Token 过期时间（秒），默认 7 天
     */
    private long refreshTokenExpirationSec = 604800;

    // ===== Getters & Setters =====

    public String getAccessTokenSecret() { return accessTokenSecret; }
    public void setAccessTokenSecret(String accessTokenSecret) { this.accessTokenSecret = accessTokenSecret; }

    public long getAccessTokenExpirationSec() { return accessTokenExpirationSec; }
    public void setAccessTokenExpirationSec(long accessTokenExpirationSec) { this.accessTokenExpirationSec = accessTokenExpirationSec; }

    public String getRefreshTokenSecret() { return refreshTokenSecret; }
    public void setRefreshTokenSecret(String refreshTokenSecret) { this.refreshTokenSecret = refreshTokenSecret; }

    public long getRefreshTokenExpirationSec() { return refreshTokenExpirationSec; }
    public void setRefreshTokenExpirationSec(long refreshTokenExpirationSec) { this.refreshTokenExpirationSec = refreshTokenExpirationSec; }
}
