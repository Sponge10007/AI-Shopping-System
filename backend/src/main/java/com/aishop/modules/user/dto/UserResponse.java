package com.aishop.modules.user.dto;

import java.time.OffsetDateTime;

public record UserResponse(
        String userId,
        String username,
        String phone,
        String role,
        String nickname,
        String avatarUrl,
        OffsetDateTime createdAt
) {
}

