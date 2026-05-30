package com.aishop.modules.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        String categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Min(0) int stock,
        List<String> tags,
        List<String> imageUrls
) {
}

