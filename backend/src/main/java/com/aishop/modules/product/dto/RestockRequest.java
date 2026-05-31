package com.aishop.modules.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RestockRequest(
        @Min(1) int quantity,
        @NotBlank String remark
) {
}

