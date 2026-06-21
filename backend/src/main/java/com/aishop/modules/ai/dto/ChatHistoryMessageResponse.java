package com.aishop.modules.ai.dto;

import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatHistoryMessageResponse(
        String role,
        String content,
        List<String> imageList,
        List<String> linkList,
        List<ProductSummaryResponse> relatedProducts,
        OffsetDateTime createdAt
) {
}
