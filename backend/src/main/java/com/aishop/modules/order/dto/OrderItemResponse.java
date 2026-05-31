package com.aishop.modules.order.dto;

public record OrderItemResponse(
        String productId,
        String name,
        String unitPrice,
        int quantity
) {
}
