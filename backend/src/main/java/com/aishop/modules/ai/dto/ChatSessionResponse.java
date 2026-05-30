package com.aishop.modules.ai.dto;

import java.time.OffsetDateTime;

public record ChatSessionResponse(
        String sessionId,
        String title,
        OffsetDateTime createdAt
) {
}

