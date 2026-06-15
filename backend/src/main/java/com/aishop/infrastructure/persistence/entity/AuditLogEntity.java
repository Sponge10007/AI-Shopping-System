package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * 审计日志表实体 — 映射数据库 audit_logs 表
 *
 * 字段设计说明：
 * - operatorId: 操作人用户 ID
 * - action: 操作类型，如 CREATE_PRODUCT、UPDATE_PRODUCT、OFF_SALE、RESTOCK、UPDATE_USER_STATUS 等
 * - targetType: 操作目标类型，如 PRODUCT、USER、ORDER
 * - targetId: 操作目标业务 ID
 * - detail: 操作详情，JSON 字符串，记录操作前后的变化
 * - result: 操作结果，SUCCESS / FAILED
 *
 * 用途：
 * - 记录关键操作日志，用于安全审计和问题追溯
 * - 管理员可以查看平台操作历史
 */
@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false, length = 32)
    private String result = "SUCCESS";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AuditLogEntity() {}

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
