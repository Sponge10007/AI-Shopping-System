package com.aishop.modules.auth.dto;

public record RegisterResponse(
        String userId,
        String username,
        String role
) {
}

