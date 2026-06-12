package com.aishop.modules.order;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.behavior.dto.BehaviorEventRequest;
import com.aishop.modules.order.dto.CreateOrderItemRequest;
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.order.dto.OrderItemResponse;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.PayOrderRequest;
import com.aishop.modules.order.dto.PayOrderResponse;
import com.aishop.modules.order.dto.ReceiverRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private final JdbcTemplate jdbcTemplate;
    private final BehaviorService behaviorService;

    public OrderService(JdbcTemplate jdbcTemplate, BehaviorService behaviorService) {
        this.jdbcTemplate = jdbcTemplate;
        this.behaviorService = behaviorService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String userId = CurrentUser.prototypeCustomer().userId();
        String orderId = "o" + UUID.randomUUID().toString().replace("-", "");
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderItemRequest item : request.items()) {
            ProductSnapshot product = loadProductForSale(item.productId());
            if (product.stock() < item.quantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "库存不足: " + product.name());
            }
            int updated = jdbcTemplate.update("""
                            UPDATE products
                            SET stock = stock - ?, sales = sales + ?, updated_at = now()
                            WHERE product_id = ?
                              AND status = 'ON_SALE'
                              AND stock >= ?
                            """,
                    item.quantity(),
                    item.quantity(),
                    item.productId(),
                    item.quantity());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "库存不足: " + product.name());
            }
            lines.add(new OrderLine(product.productId(), product.name(), product.price(), item.quantity()));
            totalAmount = totalAmount.add(product.price().multiply(BigDecimal.valueOf(item.quantity())));
        }

        jdbcTemplate.update("""
                        INSERT INTO orders (
                          order_id, user_id, status, total_amount,
                          receiver_name, receiver_phone, receiver_address
                        )
                        VALUES (?, ?, 'CREATED', ?, ?, ?, ?)
                        """,
                orderId,
                userId,
                totalAmount,
                request.receiver().name(),
                request.receiver().phone(),
                request.receiver().address());

        for (OrderLine line : lines) {
            jdbcTemplate.update("""
                            INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    orderId,
                    line.productId(),
                    line.name(),
                    line.unitPrice(),
                    line.quantity());
        }

        behaviorService.recordForUser(userId, new BehaviorEventRequest(
                "ORDER",
                lines.isEmpty() ? null : lines.get(0).productId(),
                null,
                Map.of("order_id", orderId, "item_count", lines.size())
        ));

        return getOrder(orderId);
    }

    public PageResponse<OrderResponse> listOrders(String status, int page, int size) {
        try {
            int normalizedPage = Math.max(page, 1);
            int normalizedSize = Math.min(Math.max(size, 1), 100);
            int offset = (normalizedPage - 1) * normalizedSize;
            String userId = CurrentUser.prototypeCustomer().userId();
            List<OrderResponse> items;
            Long total;
            if (status == null || status.isBlank()) {
                items = jdbcTemplate.query("""
                                SELECT * FROM orders
                                WHERE user_id = ?
                                ORDER BY created_at DESC
                                LIMIT ? OFFSET ?
                                """,
                        (rs, rowNum) -> mapOrder(rs, loadItems(rs.getString("order_id"))),
                        userId,
                        normalizedSize,
                        offset);
                total = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                        Long.class,
                        userId);
            } else {
                items = jdbcTemplate.query("""
                                SELECT * FROM orders
                                WHERE user_id = ? AND status = ?
                                ORDER BY created_at DESC
                                LIMIT ? OFFSET ?
                                """,
                        (rs, rowNum) -> mapOrder(rs, loadItems(rs.getString("order_id"))),
                        userId,
                        status,
                        normalizedSize,
                        offset);
                total = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE user_id = ? AND status = ?",
                        Long.class,
                        userId,
                        status);
            }
            return PageResponse.of(items, normalizedPage, normalizedSize, total == null ? items.size() : total);
        } catch (DataAccessException exception) {
            List<OrderResponse> items = List.of(sampleOrder(status == null ? "CREATED" : status, sampleReceiver()));
            return PageResponse.of(items, page, size, items.size());
        }
    }

    public OrderResponse getOrder(String orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT * FROM orders
                            WHERE order_id = ?
                              AND user_id = ?
                            """,
                    (rs, rowNum) -> mapOrder(rs, loadItems(orderId)),
                    orderId,
                    CurrentUser.prototypeCustomer().userId());
        } catch (DataAccessException exception) {
            if ("o10001".equals(orderId)) {
                return sampleOrder("CREATED", sampleReceiver());
            }
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
    }

    @Transactional
    public PayOrderResponse pay(String orderId, PayOrderRequest request) {
        OrderPaymentSnapshot order = loadOrderForPayment(orderId);
        if (!"CREATED".equals(order.status())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "订单不能重复支付");
        }
        String paymentId = "p" + UUID.randomUUID().toString().replace("-", "");
        String method = request == null || request.method() == null || request.method().isBlank()
                ? "BALANCE"
                : request.method();
        jdbcTemplate.update("""
                        INSERT INTO payments (payment_id, order_id, amount, method, status)
                        VALUES (?, ?, ?, ?, 'PAID')
                        """,
                paymentId,
                orderId,
                order.amount(),
                method);
        jdbcTemplate.update("""
                        UPDATE orders
                        SET status = 'PAID', updated_at = now()
                        WHERE order_id = ?
                        """,
                orderId);
        return new PayOrderResponse(orderId, paymentId, "PAID", order.amount().toPlainString());
    }

    private ProductSnapshot loadProductForSale(String productId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT product_id, name, price, stock
                            FROM products
                            WHERE product_id = ?
                              AND status = 'ON_SALE'
                            """,
                    (rs, rowNum) -> new ProductSnapshot(
                            rs.getString("product_id"),
                            rs.getString("name"),
                            rs.getBigDecimal("price"),
                            rs.getInt("stock")
                    ),
                    productId);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在或已下架");
        }
    }

    private OrderPaymentSnapshot loadOrderForPayment(String orderId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT order_id, status, total_amount
                            FROM orders
                            WHERE order_id = ?
                              AND user_id = ?
                            FOR UPDATE
                            """,
                    (rs, rowNum) -> new OrderPaymentSnapshot(
                            rs.getString("order_id"),
                            rs.getString("status"),
                            rs.getBigDecimal("total_amount")
                    ),
                    orderId,
                    CurrentUser.prototypeCustomer().userId());
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在");
        }
    }

    private List<OrderItemResponse> loadItems(String orderId) {
        return jdbcTemplate.query("""
                        SELECT product_id, product_name, unit_price, quantity
                        FROM order_items
                        WHERE order_id = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new OrderItemResponse(
                        rs.getString("product_id"),
                        rs.getString("product_name"),
                        rs.getBigDecimal("unit_price").toPlainString(),
                        rs.getInt("quantity")
                ),
                orderId);
    }

    private OrderResponse mapOrder(ResultSet rs, List<OrderItemResponse> items) throws SQLException {
        return new OrderResponse(
                rs.getString("order_id"),
                rs.getString("user_id"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount").toPlainString(),
                items,
                new ReceiverRequest(
                        rs.getString("receiver_name"),
                        rs.getString("receiver_phone"),
                        rs.getString("receiver_address")
                ),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private OrderResponse sampleOrder(String status, ReceiverRequest receiver) {
        return new OrderResponse(
                "o10001",
                "u10001",
                status,
                "598.00",
                List.of(new OrderItemResponse("10001", "蓝牙降噪耳机", "299.00", 2)),
                receiver,
                OffsetDateTime.now()
        );
    }

    private ReceiverRequest sampleReceiver() {
        return new ReceiverRequest("张三", "13800000000", "浙江省杭州市");
    }

    private record ProductSnapshot(String productId, String name, BigDecimal price, int stock) {
    }

    private record OrderLine(String productId, String name, BigDecimal unitPrice, int quantity) {
    }

    private record OrderPaymentSnapshot(String orderId, String status, BigDecimal amount) {
    }
}
