package com.aishop.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompareProductsRequest(
        @Size(min = 2, max = 4, message = "请选择2到4件商品")
        List<@NotBlank String> productIds,

        @Size(max = 500, message = "对比需求不能超过500字")
        String intent
) {
}
