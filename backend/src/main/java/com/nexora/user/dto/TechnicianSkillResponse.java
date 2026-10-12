package com.nexora.user.dto;

import java.time.OffsetDateTime;

import com.nexora.user.entity.TechnicianSkill;

public record TechnicianSkillResponse(
        Long skillId,
        String skillCode,
        String skillName,
        int proficiencyLevel,
        OffsetDateTime assignedAt
) {
    public static TechnicianSkillResponse from(TechnicianSkill ts) {
        return new TechnicianSkillResponse(
                ts.getSkill().getId(),
                ts.getSkill().getCode(),
                ts.getSkill().getName(),
                ts.getProficiencyLevel(),
                ts.getAssignedAt()
        );
    }
}
