package com.aishop.modules.product.dto;

public record ProductMutationResponse(
        String productId,
        String status,
        String vectorIndexStatus
) {
}

