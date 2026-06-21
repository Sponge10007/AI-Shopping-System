package com.aishop.modules.ai.dto;

import java.util.Map;

public record CompareDimensionResponse(
        String name,
        Map<String, Integer> scores
) {
}
