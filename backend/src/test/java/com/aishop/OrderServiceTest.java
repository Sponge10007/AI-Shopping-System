package com.aishop;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.persistence.entity.OrderEntity;
import com.aishop.infrastructure.persistence.entity.OrderItemEntity;
import com.aishop.infrastructure.persistence.repository.OrderItemRepository;
import com.aishop.infrastructure.persistence.repository.OrderRepository;
import com.aishop.infrastructure.persistence.repository.PaymentRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.modules.order.OrderService;
import com.aishop.modules.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    private static final CurrentUser CUSTOMER = CurrentUser.prototypeCustomer();

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProductRepository productRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, paymentRepository, productRepository);
    }

    @Test
    void listOrdersReturnsOnlyCurrentUserOrders() {
        OrderEntity order = order("o10001", CUSTOMER.userId(), "CREATED");
        when(orderRepository.findByUserId(eq(CUSTOMER.userId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findByOrderId("o10001")).thenReturn(List.of(item("o10001")));

        PageResponse<OrderResponse> response = orderService.listOrders(CUSTOMER, null, 1, 20);

        assertThat(response.items()).extracting(OrderResponse::orderId).containsExactly("o10001");
        assertThat(response.items()).extracting(OrderResponse::status).containsExactly("CREATED");
    }

    @Test
    void getOrderReturnsOrderForOwner() {
        when(orderRepository.findByOrderId("o10001")).thenReturn(Optional.of(order("o10001", CUSTOMER.userId(), "CREATED")));
        when(orderItemRepository.findByOrderId("o10001")).thenReturn(List.of(item("o10001")));

        OrderResponse response = orderService.getOrder(CUSTOMER, "o10001");

        assertThat(response.orderId()).isEqualTo("o10001");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getOrderRejectsDifferentUser() {
        when(orderRepository.findByOrderId("o10001")).thenReturn(Optional.of(order("o10001", "u99999", "CREATED")));

        assertThatThrownBy(() -> orderService.getOrder(CUSTOMER, "o10001"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                });
    }

    @Test
    void getOrderThrowsNotFoundForMissingOrder() {
        when(orderRepository.findByOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(CUSTOMER, "missing"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });
    }

    private OrderEntity order(String orderId, String userId, String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-16T00:00:00Z");
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("598.00"));
        order.setReceiverName("Alice");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("Hangzhou");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    private OrderItemEntity item(String orderId) {
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(orderId);
        item.setProductId("10001");
        item.setProductName("Noise Cancelling Headphones");
        item.setUnitPrice(new BigDecimal("299.00"));
        item.setQuantity(2);
        return item;
    }
}
