package com.aishop.modules.internal.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchProductAiSummaryRequest(
        @NotEmpty List<String> productIds
) {
}

