package com.aishop.modules.search.dto;

import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequest(
        @NotBlank String query,
        SearchFilters filters,
        Double distanceThreshold,
        Integer limit
) {
}

