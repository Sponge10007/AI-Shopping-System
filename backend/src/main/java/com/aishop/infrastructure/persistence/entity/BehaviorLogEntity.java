package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为日志实体
 *
 * 记录用户在平台上的关键行为事件，用于用户画像分析和推荐系统。
 *
 * 数据库表：behavior_logs
 *
 * 字段说明：
 * - id: 主键，自增
 * - userId: 用户 ID（关联 users.user_id）
 * - eventType: 事件类型（VIEW / SEARCH / ADD_TO_CART / PURCHASE）
 * - productId: 关联商品 ID（VARCHAR(64)，可为 null，如 SEARCH 事件无特定商品）
 * - query: 搜索关键词（仅 SEARCH 事件使用）
 * - metadata: 附加元数据（JSONB 格式，存储事件上下文信息）
 * - createdAt: 记录创建时间
 *
 * 修复记录（2026-06-08）：
 * - 修正 productId 字段类型：数据库 product_id 列为 VARCHAR(64)，故使用 String 而非 Long
 * - 修正 metadata 字段类型：数据库 metadata 列为 JSONB，使用 @JdbcTypeCode(SqlTypes.JSON)
 *   注解让 Hibernate 正确将 Map 序列化为 JSONB 格式写入数据库
 *
 * 修复记录（2026-06-08 v2）：
 * - 【关键修复】metadata 字段类型从 String 改为 Map<String, Object>
 *   原因：@JdbcTypeCode(SqlTypes.JSON) 配合 String 类型时，Hibernate 底层仍以 VARCHAR
 *   方式设置 PreparedStatement 参数，导致 PostgreSQL 报错：
 *   "column is of type jsonb but expression is of type character varying"
 *   改为 Map<String, Object> 后，Hibernate 使用 Jackson 将 Map 序列化为 JSON 对象，
 *   以 JSONB 格式正确写入数据库。
 */
@Entity
@Table(name = "behavior_logs")
public class BehaviorLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 关联商品 ID（VARCHAR(64)），SEARCH 事件时为 null */
    @Column(name = "product_id", length = 64)
    private String productId;

    /** 搜索关键词（仅 SEARCH 事件使用） */
    @Column(name = "query", columnDefinition = "TEXT")
    private String query;

    /**
     * 附加元数据（JSONB 格式）
     *
     * 【修复 v2】类型从 String 改为 Map<String, Object>。
     * 原因：@JdbcTypeCode(SqlTypes.JSON) 配合 String 类型时，Hibernate 仍以 VARCHAR
     * 方式写入 JSONB 列，导致 PostgreSQL 类型不匹配错误。
     * 改为 Map 后，Hibernate 使用 Jackson 序列化为 JSON 对象，以 JSONB 格式写入。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSONB")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ===== Getters & Setters =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
