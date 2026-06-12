package com.aishop.modules.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        String orderId,
        String userId,
        String status,
        String totalAmount,
        List<OrderItemResponse> items,
        ReceiverRequest receiver,
        OffsetDateTime createdAt
) {
}
