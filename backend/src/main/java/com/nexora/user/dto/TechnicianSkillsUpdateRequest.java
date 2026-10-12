package com.nexora.user.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TechnicianSkillsUpdateRequest(
        @NotNull(message = "skills must not be null")
        @Valid
        List<SkillAssignmentItem> skills,

        Long version
) {
}
