package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 订单明细表实体 — 映射数据库 order_items 表
 *
 * 字段设计说明：
 * - orderId: 关联订单 ID
 * - productId: 关联商品 ID（快照，即使商品被删除也能追溯）
 * - productName: 下单时的商品名称（快照，防止商家改名后订单记录变化）
 * - unitPrice: 下单时的单价（快照）
 * - quantity: 购买数量
 *
 * 为什么使用快照？
 * 订单是交易凭证，商品信息（名称、价格）在订单创建后不应受商家修改影响。
 * 即使商家后来修改了商品名称或价格，订单中的记录保持不变。
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    public OrderItemEntity() {}

    // ===== 业务方法 =====

    /**
     * 计算小计金额
     */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
