package com.nexora.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing EquipmentType.
 */
public record EquipmentTypeUpdateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,

        @NotNull(message = "active is required")
        Boolean active) {
}
