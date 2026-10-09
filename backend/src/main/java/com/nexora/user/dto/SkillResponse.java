package com.nexora.user.dto;

import com.nexora.user.entity.Skill;

public record SkillResponse(
        Long id,
        String code,
        String name,
        boolean active
) {
    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getCode(),
                skill.getName(),
                skill.isActive()
        );
    }
}
