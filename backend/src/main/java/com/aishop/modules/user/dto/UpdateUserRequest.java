package com.aishop.modules.user.dto;

public record UpdateUserRequest(
        String nickname,
        String phone,
        String avatarUrl
) {
}

