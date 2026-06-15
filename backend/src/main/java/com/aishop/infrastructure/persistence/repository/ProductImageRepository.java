package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品图片表数据访问层
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity> findByProductIdOrderBySortOrderAsc(String productId);

    void deleteByProductId(String productId);
}
