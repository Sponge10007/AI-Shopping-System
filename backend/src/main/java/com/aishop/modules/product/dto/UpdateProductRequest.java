package com.aishop.modules.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdateProductRequest(
        String name,
        String description,
        BigDecimal price,
        List<String> tags,
        List<String> imageUrls
) {
}

