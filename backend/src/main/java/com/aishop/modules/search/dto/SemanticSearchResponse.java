package com.aishop.modules.search.dto;

import com.aishop.modules.product.dto.ProductSummaryResponse;

import java.util.List;

public record SemanticSearchResponse(
        String query,
        boolean relaxed,
        List<ProductSummaryResponse> items
) {
}

