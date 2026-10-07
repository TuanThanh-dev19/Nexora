package com.nexora.category.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexora.category.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Version 1 of the reference/admin form contract. Entities never cross the HTTP boundary. */
public final class CategoryContracts {
    private CategoryContracts() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull AssetPolicy assetPolicy, boolean locationRequired,
            @NotNull IssuePriority defaultPriority, boolean active) {}

    public record CategoryResponse(Long id, String code, String name, String description,
            AssetPolicy assetPolicy, boolean locationRequired, IssuePriority defaultPriority, boolean active) {
        public static CategoryResponse from(IncidentCategory c) {
            return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getAssetPolicy(), c.isLocationRequired(), c.getDefaultPriority(), c.isActive());
        }
    }

    public record RevisionRequest(@NotNull @PositiveOrZero Long revision) {}

    public record DraftRequest(@NotNull @PositiveOrZero Long revision, boolean requiresEquipment,
            @Min(0) int minAttachments, @Positive Long defaultSkillId, @Positive Long defaultSlaPolicyId,
            @NotNull @Size(max = 100) List<@NotNull @Valid FieldRequest> fields) {}

    public record FieldRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") String fieldKey,
            @NotBlank @Size(max = 150) String label, @NotNull DynamicFieldType fieldType,
            @Size(max = 500) String helpText, @Size(max = 250) String placeholder,
            boolean required, boolean active, @Min(0) int displayOrder,
            @NotNull JsonNode validationRules,
            @NotNull @Size(max = 200) List<@NotNull @Valid OptionRequest> options) {}

    public record OptionRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_.-]+") String optionValue,
            @NotBlank @Size(max = 150) String optionLabel, @Min(0) int displayOrder, boolean active) {}

    public record OptionResponse(Long id, String optionValue, String optionLabel, int displayOrder, boolean active) {}

    public record FieldResponse(Long id, String fieldKey, String label, DynamicFieldType fieldType,
            String helpText, String placeholder, boolean required, boolean active, int displayOrder,
            JsonNode validationRules, List<OptionResponse> options) {}

    public record SkillResponse(Long id, String code, String name, boolean active) {}

    public record SlaResponse(Long id, String policyName, IssuePriority priority, int resolutionMinutes,
            boolean pauseWaitingRequester, boolean active) {}

    public record VersionResponse(Long id, int versionNumber, FormStatus status, long revision,
            OffsetDateTime createdAt, OffsetDateTime publishedAt) {}

    public record FormResponse(int schemaVersion, Long id, CategoryResponse category, int versionNumber,
            FormStatus status, long revision, boolean requiresEquipment, int minAttachments,
            Long defaultSkillId, Long defaultSlaPolicyId, SkillResponse defaultSkill, SlaResponse defaultSlaPolicy,
            OffsetDateTime createdAt, OffsetDateTime publishedAt, List<FieldResponse> fields) {}
}
