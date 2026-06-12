package com.aishop.modules.order.dto;

import jakarta.validation.constraints.NotBlank;

public record ReceiverRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String address
) {
}

