package com.aishop.modules.order;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.PayOrderRequest;
import com.aishop.modules.order.dto.PayOrderResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器
 *
 * 接口路径说明：
 * - POST /api/v1/orders — 创建订单（需要登录）
 * - GET /api/v1/orders — 查询当前用户的订单列表（需要登录）
 * - GET /api/v1/orders/{orderId} — 获取订单详情（需要登录）
 * - POST /api/v1/orders/{orderId}/pay — 支付订单（需要登录）
 *
 * 认证方式：
 * - 所有订单接口需要 Bearer Token，从 SecurityContext 获取当前用户
 * - 用户只能查看和操作自己的订单
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 创建订单
     *
     * 买家提交购物车中的商品，创建新订单。
     * 涉及事务：扣减库存 + 创建订单 + 创建订单项
     */
    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateOrderRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(orderService.createOrder(currentUser, request));
    }

    /**
     * 查询当前用户的订单列表
     *
     * 支持按状态筛选和分页
     */
    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> listOrders(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(orderService.listOrders(currentUser, status, page, size));
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String orderId) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(orderService.getOrder(currentUser, orderId));
    }

    /**
     * 支付订单
     *
     * 买家支付一个 CREATED 状态的订单。
     * 支付成功后，订单状态变为 PAID。
     */
    @PostMapping("/{orderId}/pay")
    public ApiResponse<PayOrderResponse> pay(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable String orderId,
            @RequestBody PayOrderRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(orderService.pay(currentUser, orderId, request));
    }
}
