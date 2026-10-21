package com.nexora.ticket.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@JsonDeserialize(using = TicketActionRequestReader.Start.class)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StartTicketRequest(
        @NotNull(message = "Ticket version is required.")
        @PositiveOrZero(message = "Ticket version must be non-negative.") Long version) {}
