package com.aishop.modules.admin.dto;

import java.time.OffsetDateTime;

public record AdminUserResponse(
        String userId,
        String username,
        String phone,
        String role,
        String status,
        OffsetDateTime createdAt
) {
}

