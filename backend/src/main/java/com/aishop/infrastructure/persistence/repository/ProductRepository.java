package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 商品表数据访问层
 *
 * 提供的方法：
 * - findByProductId: 根据业务主键查询商品
 * - findByStatus: 按状态分页查询（如查询所有上架商品）
 * - findByStatusAndCategoryId: 按状态+分类分页查询
 * - findByMerchantId: 查询卖家的所有商品
 * - findByMerchantIdAndStatus: 按卖家+状态查询
 * - increaseStock: 原子补货操作（防止并发）
 * - decreaseStock: 原子扣减库存（防止并发）
 * - findMaxProductIdNumeric: 查询数据库中最大的 productId 数字部分
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByProductId(String productId);

    boolean existsByProductId(String productId);

    long countByStatus(String status);

    // 公开查询：在售商品
    Page<ProductEntity> findByStatus(String status, Pageable pageable);

    // 公开查询：在售商品 + 分类筛选
    Page<ProductEntity> findByStatusAndCategoryId(String status, String categoryId, Pageable pageable);

    // 卖家查询自己的商品（无分页）
    List<ProductEntity> findByMerchantId(String merchantId);

    // 卖家按状态查询自己的商品（有分页）
    Page<ProductEntity> findByMerchantIdAndStatus(String merchantId, String status, Pageable pageable);

    // 卖家查询所有商品（有分页，不分状态）
    Page<ProductEntity> findByMerchantId(String merchantId, Pageable pageable);

    // 查询数据库中最大的 productId 数字部分（用于初始化计数器）
    // 注意：JPQL 中必须使用 Java 实体类的属性名（驼峰），而非数据库列名（下划线）
    @Query("SELECT MAX(CAST(SUBSTRING(p.productId, 2) AS long)) FROM ProductEntity p")
    Long findMaxProductIdNumeric();

    // 原子补货 — 使用参数传递时间，避免 CURRENT_TIMESTAMP 与 OffsetDateTime 类型不匹配
    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock + :quantity, p.updatedAt = :now WHERE p.productId = :productId")
    int increaseStock(@Param("productId") String productId, @Param("quantity") Integer quantity, @Param("now") OffsetDateTime now);

    // 原子扣减库存 — 使用参数传递时间
    @Modifying
    @Query("UPDATE ProductEntity p SET p.stock = p.stock - :quantity, p.sales = p.sales + :quantity, p.updatedAt = :now WHERE p.productId = :productId AND p.stock >= :quantity")
    int decreaseStock(@Param("productId") String productId, @Param("quantity") Integer quantity, @Param("now") OffsetDateTime now);
}
