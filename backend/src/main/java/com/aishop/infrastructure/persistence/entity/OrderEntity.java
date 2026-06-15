package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 订单表实体 — 映射数据库 orders 表
 *
 * 字段设计说明：
 * - orderId: 业务主键（对外暴露，格式: o + 数字）
 * - userId: 买家ID（关联 users 表的 userId）
 * - status: CREATED（待支付）→ PAID（已支付）→ SHIPPED（已发货）→ DELIVERED（已收货）→ CANCELLED（已取消）
 * - totalAmount: 订单总金额，使用 BigDecimal 避免浮点数精度丢失
 * - receiver: 收货信息（姓名/电话/地址），下单时快照
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 32)
    private String status = "CREATED";

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 32)
    private String receiverPhone;

    @Column(name = "receiver_address", nullable = false, columnDefinition = "TEXT")
    private String receiverAddress;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public OrderEntity() {}

    // ===== 业务方法 =====

    /**
     * 检查订单是否可支付（只有 CREATED 状态的订单才能支付）
     */
    public boolean isPayable() {
        return "CREATED".equals(this.status);
    }

    /**
     * 检查订单是否已支付
     */
    public boolean isPaid() {
        return "PAID".equals(this.status);
    }

    /**
     * 检查订单是否可取消（只有 CREATED 状态的订单才能取消）
     */
    public boolean isCancellable() {
        return "CREATED".equals(this.status);
    }

    /**
     * 支付订单（状态流转：CREATED → PAID）
     */
    public void pay() {
        this.status = "PAID";
    }

    /**
     * 取消订单（状态流转：CREATED → CANCELLED）
     */
    public void cancel() {
        this.status = "CANCELLED";
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
