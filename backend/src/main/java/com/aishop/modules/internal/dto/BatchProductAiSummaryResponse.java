package com.aishop.modules.internal.dto;

import java.util.List;

public record BatchProductAiSummaryResponse(
        List<ProductAiSummaryResponse> items
) {
}

