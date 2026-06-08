package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单明细表数据访问层
 *
 * 提供的方法：
 * - findByOrderId: 根据订单 ID 查询所有订单项
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(String orderId);
}
