package com.aishop.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String phone,
        @NotBlank String password,
        @Pattern(regexp = "CUSTOMER|MERCHANT") String role
) {
}

