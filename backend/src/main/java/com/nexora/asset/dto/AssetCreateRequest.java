package com.nexora.asset.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssetCreateRequest(
        @NotBlank(message = "Asset code is required")
        @Size(max = 80, message = "Asset code must not exceed 80 characters")
        String assetCode,

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
) {
    public AssetCreateRequest {
        assetCode = assetCode != null ? assetCode.strip() : null;
        serialNumber = (serialNumber != null && !serialNumber.isBlank()) ? serialNumber.strip() : null;
        description = (description != null && !description.isBlank()) ? description.strip() : null;
    }
}
