package com.nexora.user.dto;

import com.nexora.user.entity.UserStatus;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "status must not be null")
        UserStatus status,

        Long version
) {
}
