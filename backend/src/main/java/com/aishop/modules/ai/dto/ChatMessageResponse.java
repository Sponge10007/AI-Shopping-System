package com.aishop.modules.ai.dto;

import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.util.List;

public record ChatMessageResponse(
        String sessionId,
        String answer,
        List<ProductSummaryResponse> products
) {
}

