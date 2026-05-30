package com.aishop.modules.order;

import com.aishop.common.response.ApiResponse;
import com.aishop.common.response.PageResponse;
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.PayOrderRequest;
import com.aishop.modules.order.dto.PayOrderResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.createOrder(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(orderService.listOrders(status, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable String orderId) {
        return ApiResponse.ok(orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<PayOrderResponse> pay(
            @PathVariable String orderId,
            @RequestBody PayOrderRequest request
    ) {
        return ApiResponse.ok(orderService.pay(orderId, request));
    }
}

