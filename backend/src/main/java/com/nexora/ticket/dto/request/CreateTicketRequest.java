package com.nexora.ticket.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** Client-owned submission data only; unknown properties are rejected by the multipart reader. */
@io.swagger.v3.oas.annotations.media.Schema(
        additionalProperties =
                io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record CreateTicketRequest(
        @NotNull @Positive Long categoryId,
        @NotNull @Positive Long formVersionId,
        @NotNull @Positive Long locationId,
        @Positive Long assetId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull @Size(max = 100) List<@NotNull @Valid DynamicValue> dynamicValues,
        @NotNull @Size(max = 5) List<@NotNull @Valid Attachment> attachments) {
    public CreateTicketRequest {
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
    }

    public record DynamicValue(
            @NotBlank @Size(max = 80) String fieldKey, @NotNull JsonNode value) {}

    public record Attachment(@NotNull @Min(0) Integer fileIndex, @Size(max = 80) String fieldKey) {}
}
