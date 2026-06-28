package com.aishop;

import com.aishop.infrastructure.ai.AiServiceClient;
import com.aishop.infrastructure.persistence.entity.UserEntity;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        when(userRepository.countByStatus("ACTIVE")).thenReturn(2L);
        when(productRepository.countByStatus("ON_SALE")).thenReturn(2L);
        when(orderRepository.countByStatus("CREATED")).thenReturn(1L);
        when(orderRepository.countByStatus("PAID")).thenReturn(0L);
        when(orderRepository.countByCreatedAtAfter(any(OffsetDateTime.class))).thenReturn(1L);
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
        assertThat(response.activeUserCount()).isEqualTo(2);
        assertThat(response.onSaleProductCount()).isEqualTo(2);
        assertThat(response.pendingOrderCount()).isEqualTo(1);
        assertThat(response.paidOrderCount()).isZero();
        assertThat(response.aiServiceStatus()).isEqualTo("DOWN");
        assertThat(response.vectorDbStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void listUsersAppliesNormalizedRoleKeywordAndPagination() {
        UserEntity user = user("u10001", "Alice", "13800000000", "CUSTOMER");
        when(userRepository.findAdminUsers(eq("CUSTOMER"), eq("alice"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        var response = adminService.listUsers("customer", " Alice ", 2, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).userId()).isEqualTo("u10001");
        verify(userRepository).findAdminUsers(
                eq("CUSTOMER"),
                eq("alice"),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 1
                                && pageable.getPageSize() == 10
                                && pageable.getSort().getOrderFor("createdAt") != null)
        );
    }

    @Test
    void listUsersUsesEmptyStringsForMissingFiltersToKeepPostgresTypesStable() {
        when(userRepository.findAdminUsers(eq(""), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var response = adminService.listUsers(null, "   ", 1, 100);

        assertThat(response.items()).isEmpty();
        verify(userRepository).findAdminUsers(eq(""), eq(""), any(Pageable.class));
    }

    private UserEntity user(String userId, String username, String phone, String role) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

}
