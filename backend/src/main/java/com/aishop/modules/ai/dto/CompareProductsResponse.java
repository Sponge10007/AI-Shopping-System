package com.aishop.modules.ai.dto;

import java.util.List;

public record CompareProductsResponse(
        String source,
        String intent,
        String winnerProductId,
        String summary,
        List<String> highlights,
        List<CompareItemResponse> items,
        List<CompareDimensionResponse> dimensions
) {
}
