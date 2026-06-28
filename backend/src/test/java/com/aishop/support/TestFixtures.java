package com.aishop.support;

import com.aishop.common.response.PageResponse;
import com.aishop.modules.admin.dto.AdminMetricsResponse;
import com.aishop.modules.admin.dto.AdminUserResponse;
import com.aishop.modules.auth.dto.LoginResponse;
import com.aishop.modules.auth.dto.RegisterResponse;
import com.aishop.modules.order.dto.OrderItemResponse;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.ReceiverRequest;
import com.aishop.modules.product.dto.ProductResponse;
import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

public final class TestFixtures {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-16T00:00:00Z");

    private TestFixtures() {
    }

    public static RegisterResponse customerRegisterResponse() {
        return new RegisterResponse("u10001", "alice", "CUSTOMER");
    }

    public static LoginResponse loginResponse() {
        return new LoginResponse(
                "dev-access-token",
                "dev-refresh-token",
                7200,
                customerRegisterResponse()
        );
    }

    public static ProductSummaryResponse productSummary(String productId) {
        return productSummary(productId, "299.00", 120);
    }

    public static ProductSummaryResponse productSummary(String productId, String price, int stock) {
        return new ProductSummaryResponse(
                productId,
                "Noise Cancelling Headphones",
                price,
                stock,
                "https://example.com/products/" + productId + "/main.jpg",
                "https://example.com/products/" + productId,
                320,
                4.8,
                List.of("audio", "commute"),
                0.93,
                "matched by test fixture"
        );
    }

    public static ProductResponse product(String productId) {
        return new ProductResponse(
                productId,
                "m10001",
                "Noise Cancelling Headphones",
                "Portable headphones for commute and study",
                "c_headphone",
                "Headphones",
                "299.00",
                120,
                320,
                4.8,
                "ON_SALE",
                List.of("audio", "commute"),
                List.of("https://example.com/products/" + productId + "/main.jpg"),
                "https://example.com/products/" + productId,
                NOW,
                NOW
        );
    }

    public static PageResponse<ProductSummaryResponse> productSummaryPage() {
        return PageResponse.of(List.of(productSummary("10001")), 1, 20, 1);
    }

    public static PageResponse<ProductResponse> productPage() {
        return PageResponse.of(List.of(product("10001")), 1, 20, 1);
    }

    public static OrderResponse order(String orderId, String status) {
        return new OrderResponse(
                orderId,
                "u10001",
                status,
                "598.00",
                List.of(new OrderItemResponse("10001", "Noise Cancelling Headphones", "299.00", 2)),
                new ReceiverRequest("Alice", "13800000000", "Hangzhou"),
                NOW
        );
    }

    public static PageResponse<OrderResponse> orderPage() {
        return PageResponse.of(List.of(order("o10001", "CREATED")), 1, 20, 1);
    }

    public static AdminUserResponse adminUser(String userId, String role) {
        return new AdminUserResponse(userId, role.toLowerCase(), "13800000000", role, "ACTIVE", NOW);
    }

    public static PageResponse<AdminUserResponse> adminUserPage() {
        return PageResponse.of(List.of(adminUser("u10001", "CUSTOMER")), 1, 20, 1);
    }

    public static AdminMetricsResponse adminMetrics() {
        return new AdminMetricsResponse(3, 2, 1, 1, 0, 0, 3, 2, 1, 0, "UP", "UP");
    }
}
