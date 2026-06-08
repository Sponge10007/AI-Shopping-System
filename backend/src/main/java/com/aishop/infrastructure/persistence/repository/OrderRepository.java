package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 订单表数据访问层
 *
 * 提供的方法：
 * - findByOrderId: 根据业务主键查询订单
 * - findByUserId: 按买家分页查询订单
 * - findByUserIdAndStatus: 按买家+状态分页查询
 * - findMaxOrderIdNumeric: 查询数据库中最大的 orderId 数字部分
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    // 买家查询自己的订单
    Page<OrderEntity> findByUserId(String userId, Pageable pageable);

    // 买家按状态查询自己的订单
    Page<OrderEntity> findByUserIdAndStatus(String userId, String status, Pageable pageable);

    // 查询数据库中最大的 orderId 数字部分（用于初始化计数器）
    @Query("SELECT MAX(CAST(SUBSTRING(o.orderId, 2) AS long)) FROM OrderEntity o")
    Long findMaxOrderIdNumeric();
}
