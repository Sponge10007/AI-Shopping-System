package com.aishop.infrastructure.persistence.repository;

import com.aishop.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 支付记录表数据访问层
 *
 * 提供的方法：
 * - findByPaymentId: 根据业务主键查询支付记录
 * - findByOrderId: 根据订单 ID 查询支付记录
 * - findMaxPaymentIdNumeric: 查询数据库中最大的 paymentId 数字部分
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByPaymentId(String paymentId);

    Optional<PaymentEntity> findByOrderId(String orderId);

    // 查询数据库中最大的 paymentId 数字部分（用于初始化计数器）
    @Query(value = """
            SELECT MAX(CAST(SUBSTRING(payment_id FROM 4) AS BIGINT))
            FROM payments
            WHERE payment_id ~ '^pay[0-9]+$'
            """, nativeQuery = true)
    Long findMaxPaymentIdNumeric();
}
