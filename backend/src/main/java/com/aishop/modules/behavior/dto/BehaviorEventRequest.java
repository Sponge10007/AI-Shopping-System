package com.aishop.modules.behavior.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record BehaviorEventRequest(
        @NotBlank String eventType,
        String productId,
        String query,
        Map<String, Object> metadata
) {
}

