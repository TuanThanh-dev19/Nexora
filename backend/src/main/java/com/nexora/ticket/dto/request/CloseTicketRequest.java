package com.nexora.ticket.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonDeserialize(using = TicketActionRequestReader.Close.class)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CloseTicketRequest(
        @NotNull(message = "Ticket version is required.")
        @PositiveOrZero(message = "Ticket version must be non-negative.") Long version,
        @Size(max = 1000, message = "Reason cannot exceed 1000 characters.") String reason) {}
