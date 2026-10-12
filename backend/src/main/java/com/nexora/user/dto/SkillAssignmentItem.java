package com.nexora.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SkillAssignmentItem(
        @NotNull(message = "skillId must not be null")
        Long skillId,

        @NotNull(message = "proficiencyLevel must not be null")
        @Min(value = 1, message = "proficiencyLevel must be between 1 and 5")
        @Max(value = 5, message = "proficiencyLevel must be between 1 and 5")
        Integer proficiencyLevel
) {
}
