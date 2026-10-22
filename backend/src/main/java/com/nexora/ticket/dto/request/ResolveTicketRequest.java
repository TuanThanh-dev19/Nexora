package com.nexora.ticket.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nexora.ticket.entity.ResolutionOutcome;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonDeserialize(using = TicketActionRequestReader.Resolve.class)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ResolveTicketRequest(
        @NotNull(message = "Ticket version is required.")
        @PositiveOrZero(message = "Ticket version must be non-negative.")
        Long version,

        @NotNull(message = "Resolution outcome is required.")
        ResolutionOutcome resolutionOutcome,

        @NotBlank(message = "Resolution summary is required.")
        @Size(max = 1000, message = "Resolution summary must be between 1 and 1000 characters.")
        String resolutionSummary) {}
