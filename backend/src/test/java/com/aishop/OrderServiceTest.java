package com.aishop;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.modules.behavior.BehaviorService;
import com.aishop.modules.order.OrderService;
import com.aishop.modules.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private BehaviorService behaviorService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(jdbcTemplate, behaviorService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listOrdersFallsBackToSampleDataWhenDatabaseUnavailable() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        PageResponse<OrderResponse> response = orderService.listOrders(null, 1, 20);

        assertThat(response.items()).extracting(OrderResponse::orderId).containsExactly("o10001");
        assertThat(response.items()).extracting(OrderResponse::status).containsExactly("CREATED");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getOrderReturnsSampleForKnownFallbackOrder() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        OrderResponse response = orderService.getOrder("o10001");

        assertThat(response.orderId()).isEqualTo("o10001");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getOrderThrowsNotFoundForUnknownOrderWhenDatabaseUnavailable() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> orderService.getOrder("missing"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });
    }
}
