package com.aishop.modules.ai.dto;

import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.util.List;

public record ChatMessageResponse(
        String sessionId,
        String answer,
        List<String> imageList,
        List<String> linkList,
        String rawAnswer,
        List<ProductSummaryResponse> relatedProducts
) {
}
