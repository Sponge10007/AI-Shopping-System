package com.aishop.modules.order.dto;

public record PayOrderResponse(
        String orderId,
        String paymentId,
        String status,
        String amount
) {
}
