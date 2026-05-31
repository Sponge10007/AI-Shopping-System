package com.aishop.modules.product.dto;

public record RestockResponse(
        String productId,
        int stock
) {
}

