package com.nexora.asset.dto;

import com.nexora.asset.entity.AssetStatus;
import com.nexora.common.concurrency.VersionedRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssetStatusChangeRequest(
        @NotNull(message = "Target status is required")
        AssetStatus targetStatus,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason,

        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must be zero or positive")
        Long version
) implements VersionedRequest {
    public AssetStatusChangeRequest {
        reason = reason != null ? reason.strip() : null;
    }
}
