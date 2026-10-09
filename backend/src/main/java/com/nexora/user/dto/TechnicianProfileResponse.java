package com.nexora.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;

public record TechnicianProfileResponse(
        Long technicianId,
        String email,
        String fullName,
        boolean available,
        int maxActiveTickets,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<TechnicianSkillResponse> skills,
        List<TechnicianServiceAreaResponse> serviceAreas
) {
    public static TechnicianProfileResponse from(
            TechnicianProfile profile,
            List<TechnicianSkill> skills,
            List<TechnicianServiceArea> serviceAreas
    ) {
        List<TechnicianSkillResponse> skillResponses = skills != null
                ? skills.stream().map(TechnicianSkillResponse::from).toList()
                : List.of();

        List<TechnicianServiceAreaResponse> areaResponses = serviceAreas != null
                ? serviceAreas.stream().map(TechnicianServiceAreaResponse::from).toList()
                : List.of();

        return new TechnicianProfileResponse(
                profile.getTechnicianId(),
                profile.getUser() != null ? profile.getUser().getEmail() : null,
                profile.getUser() != null ? profile.getUser().getFullName() : null,
                profile.isAvailable(),
                profile.getMaxActiveTickets(),
                profile.getVersion(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                skillResponses,
                areaResponses
        );
    }
}
