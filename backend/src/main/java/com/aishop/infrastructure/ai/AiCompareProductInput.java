package com.aishop.infrastructure.ai;

import java.util.List;

public record AiCompareProductInput(
        String productId,
        String name,
        String description,
        String category,
        String price,
        int stock,
        int sales,
        double rating,
        List<String> tags
) {
}
