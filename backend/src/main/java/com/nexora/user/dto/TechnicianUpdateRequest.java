package com.nexora.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TechnicianUpdateRequest(
        @NotNull(message = "available must not be null")
        Boolean available,

        @NotNull(message = "maxActiveTickets must not be null")
        @Positive(message = "maxActiveTickets must be greater than zero")
        Integer maxActiveTickets,

        Long version
) {
}
