package com.nexora.ticket.dto.request;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nexora.ticket.entity.WorkLogType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@JsonDeserialize(using = TicketActionRequestReader.AddWorkLog.class)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AddWorkLogRequest(
        @NotNull(message = "Ticket version is required.")
        @PositiveOrZero(message = "Ticket version must be non-negative.")
        Long version,

        @NotNull(message = "Log type is required.")
        WorkLogType logType,

        @NotBlank(message = "Work log content cannot be blank.")
        String content,

        @NotNull(message = "StartedAt timestamp is required.")
        OffsetDateTime startedAt,

        OffsetDateTime endedAt,

        @Positive(message = "Minutes spent must be a positive integer.")
        Integer minutesSpent) {}
