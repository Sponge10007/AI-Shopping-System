package com.aishop.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商品表实体 — 映射数据库 products 表
 *
 * 字段设计说明：
 * - productId: 业务主键（对外暴露，格式: p + 数字）
 * - merchantId: 卖家ID（关联 users 表的 userId）
 * - price: 使用 BigDecimal 避免浮点数精度丢失
 * - stock: 库存数量
 * - sales: 销量（冗余字段，每次下单后更新）
 * - rating: 评分（0~5.00）
 * - status: ON_SALE（上架）/ OFF_SALE（下架）
 * - tags: JSON 字符串，如 ["蓝牙","降噪"]
 * - images: 通过 ProductImageEntity 关联存储
 */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true, length = 64)
    private String productId;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id", length = 64)
    private String categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false)
    private Integer sales = 0;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(length = 32)
    private String status = "ON_SALE";

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ProductEntity() {}

    // ===== 业务方法 =====

    /**
     * 检查商品是否上架
     */
    public boolean isOnSale() {
        return "ON_SALE".equals(this.status);
    }

    /**
     * 检查商品是否下架
     */
    public boolean isOffSale() {
        return "OFF_SALE".equals(this.status);
    }

    /**
     * 检查库存是否充足
     */
    public boolean hasSufficientStock(int quantity) {
        return this.stock >= quantity;
    }

    /**
     * 扣减库存（调用前需先校验库存充足）
     */
    public void decreaseStock(int quantity) {
        this.stock -= quantity;
    }

    /**
     * 增加库存（补货）
     */
    public void increaseStock(int quantity) {
        this.stock += quantity;
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
