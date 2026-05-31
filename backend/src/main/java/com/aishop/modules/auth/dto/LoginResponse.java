package com.aishop.modules.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        RegisterResponse user
) {
}

