package com.aishop.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiCompareResult(
        @JsonProperty("winner_product_id")
        String winnerProductId,
        String summary,
        List<String> highlights,
        List<AiCompareItemResult> items,
        List<AiCompareDimensionResult> dimensions
) {
}
