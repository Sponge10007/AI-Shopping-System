package com.aishop.modules.product.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProductResponse(
        String productId,
        String merchantId,
        String name,
        String description,
        String categoryId,
        String categoryName,
        String price,
        int stock,
        int sales,
        double rating,
        String status,
        List<String> tags,
        List<String> imageUrls,
        String detailUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
