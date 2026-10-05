package com.nexora.location.dto;

import com.nexora.location.entity.LocationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new Location.
 */
public record LocationCreateRequest(

        @NotBlank(message = "code is required")
        @Size(max = 50, message = "code must not exceed 50 characters")
        String code,

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,

        @NotNull(message = "type is required")
        LocationType type,

        Long parentId,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description) {
}
