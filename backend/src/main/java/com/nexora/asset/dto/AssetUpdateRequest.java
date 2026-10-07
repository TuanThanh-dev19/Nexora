package com.nexora.asset.dto;

import java.time.LocalDate;

import com.nexora.common.concurrency.VersionedRequest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssetUpdateRequest(
        @NotNull(message = "Version is required")
        @PositiveOrZero(message = "Version must be zero or positive")
        Long version,

        @NotNull(message = "Equipment type is required")
        Long equipmentTypeId,

        @NotNull(message = "Location is required")
        Long locationId,

        @Size(max = 120, message = "Serial number must not exceed 120 characters")
        String serialNumber,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        LocalDate purchasedAt,

        LocalDate warrantyUntil
) implements VersionedRequest {
    public AssetUpdateRequest {
        serialNumber = (serialNumber != null && !serialNumber.isBlank()) ? serialNumber.strip() : null;
        description = (description != null && !description.isBlank()) ? description.strip() : null;
    }
}
