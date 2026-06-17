package com.aishop;

import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.infrastructure.persistence.entity.OrderEntity;
import com.aishop.infrastructure.persistence.repository.BehaviorLogRepository;
import com.aishop.infrastructure.persistence.repository.OrderRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.infrastructure.persistence.repository.UserRepository;
import com.aishop.modules.admin.AdminService;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BehaviorLogRepository behaviorLogRepository;
    @Mock
    private AiServiceClient aiServiceClient;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                productRepository,
                orderRepository,
                behaviorLogRepository,
                aiServiceClient
        );
    }

    @Test
    void overviewCountsRepositoriesAndAiStatus() {
        when(userRepository.count()).thenReturn(3L);
        when(productRepository.count()).thenReturn(2L);
        when(orderRepository.count()).thenReturn(1L);
        when(orderRepository.findAll()).thenReturn(List.of(todayOrder()));
        when(behaviorLogRepository.countByEventTypeAndCreatedAtAfter(anyString(), any(OffsetDateTime.class)))
                .thenReturn(2L);
        when(aiServiceClient.health()).thenReturn(false);

        AdminMetricsResponse response = adminService.overview();

        assertThat(response.userCount()).isEqualTo(3);
        assertThat(response.productCount()).isEqualTo(2);
        assertThat(response.orderCount()).isEqualTo(1);
        assertThat(response.todayOrderCount()).isEqualTo(1);
        assertThat(response.searchCountToday()).isEqualTo(4);
        assertThat(response.aiChatCountToday()).isEqualTo(2);
        assertThat(response.aiServiceStatus()).isEqualTo("DOWN");
        assertThat(response.vectorDbStatus()).isEqualTo("UNKNOWN");
    }

    private OrderEntity todayOrder() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("o10001");
        order.setUserId("u10001");
        order.setStatus("CREATED");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        return order;
    }
}
