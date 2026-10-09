package com.nexora.user.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record TechnicianServiceAreasUpdateRequest(
        @NotNull(message = "locationIds must not be null")
        List<Long> locationIds,

        Long version
) {
}
