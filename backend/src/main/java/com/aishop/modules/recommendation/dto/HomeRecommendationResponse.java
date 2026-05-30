package com.aishop.modules.recommendation.dto;

import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.util.List;

public record HomeRecommendationResponse(
        String strategy,
        List<ProductSummaryResponse> items
) {
}

