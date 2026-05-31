package com.aishop.modules.search.dto;

import java.math.BigDecimal;

public record SearchFilters(
        String categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock
) {
}

