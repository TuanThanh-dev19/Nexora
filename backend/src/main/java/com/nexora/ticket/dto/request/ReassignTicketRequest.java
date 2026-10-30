package com.nexora.ticket.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonDeserialize(using = TicketActionRequestReader.Reassign.class)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ReassignTicketRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Positive Long technicianId,
        @NotBlank @Size(max = 1000) String reason) {
    public ReassignTicketRequest {
        reason = reason == null ? null : reason.trim();
    }
}
