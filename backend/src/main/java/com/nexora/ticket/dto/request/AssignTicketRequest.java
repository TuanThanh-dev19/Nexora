package com.nexora.ticket.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AssignTicketRequest(
        @NotNull(message = "Ticket version is required.")
        @PositiveOrZero(message = "Ticket version must be non-negative.")
        Long version,

        @NotNull(message = "Technician ID is required.")
        @Positive(message = "Technician ID must be positive.")
        Long technicianId) {
}
