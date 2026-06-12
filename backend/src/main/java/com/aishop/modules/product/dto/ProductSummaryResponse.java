package com.aishop.modules.product.dto;

import java.util.List;

public record ProductSummaryResponse(
        String productId,
        String name,
        String price,
        int stock,
        String imageUrl,
        String detailUrl,
        int sales,
        double rating,
        List<String> tags,
        Double score,
        String reason
) {
}
