package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * 用户表实体 — 映射数据库 users 表
 *
 * 字段设计说明：
 * - id: 自增主键（内部使用，不对外暴露）
 * - userId: 业务主键（对外暴露，格式: u + 数字 或 m + 数字）
 * - username: 用户名，唯一
 * - phone: 手机号，用于登录或找回密码
 * - passwordHash: BCrypt 哈希后的密码，绝不存储明文
 * - role: 角色，CUSTOMER（普通用户）或 MERCHANT（商家）
 * - status: 状态，ACTIVE（正常）/ DISABLED（禁用）
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(length = 32)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserEntity() {}

    // ===== 业务方法 =====

    /**
     * 只有 ACTIVE 状态允许登录和使用已签发的令牌。
     * 对未知状态采用安全失败策略，避免状态拼写错误意外放行。
     */
    public boolean isActive() {
        return "ACTIVE".equals(this.status);
    }

    /**
     * 检查密码是否匹配（由 AuthService 调用 BCrypt 校验）
     */
    public boolean isPasswordMatch(String rawPassword, java.util.function.BiFunction<String, String, Boolean> checker) {
        return checker.apply(rawPassword, this.passwordHash);
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
