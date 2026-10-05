package com.nexora.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing Location.
 *
 * <p>Code is intentionally excluded — code changes are not allowed after creation
 * to preserve data integrity across references.
 */
public record LocationUpdateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,

        Long parentId,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,

        @NotNull(message = "active is required")
        Boolean active) {
}
