package com.aishop.infrastructure.ai;

import java.util.Map;

public record AiCompareDimensionResult(
        String name,
        Map<String, Integer> scores
) {
}
