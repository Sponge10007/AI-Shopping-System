package com.aishop.modules.ai.dto;

import java.util.List;

public record CompareItemResponse(
        String productId,
        int score,
        String verdict,
        List<String> strengths,
        List<String> weaknesses
) {
}
