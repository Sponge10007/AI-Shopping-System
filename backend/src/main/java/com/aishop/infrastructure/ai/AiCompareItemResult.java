package com.aishop.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiCompareItemResult(
        @JsonProperty("product_id")
        String productId,
        Integer score,
        String verdict,
        List<String> strengths,
        List<String> weaknesses
) {
}
