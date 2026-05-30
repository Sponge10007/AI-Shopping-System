package com.aishop.modules.admin.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @Pattern(regexp = "ACTIVE|DISABLED") String status
) {
}

