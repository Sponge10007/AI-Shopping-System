package com.aishop.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiImageSearchResult(
        List<String> keywords,
        String query,
        @JsonProperty("product_ids")
        List<String> productIds
) {
    public static AiImageSearchResult empty() {
        return new AiImageSearchResult(List.of(), "", List.of());
    }
}
