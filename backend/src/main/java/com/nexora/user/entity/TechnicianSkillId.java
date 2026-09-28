package com.nexora.user.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TechnicianSkillId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "technician_id")
    private Long technicianId;

    @Column(name = "skill_id")
    private Long skillId;

    protected TechnicianSkillId() {
    }

    public TechnicianSkillId(Long technicianId, Long skillId) {
        this.technicianId = Objects.requireNonNull(technicianId, "technicianId is required");
        this.skillId = Objects.requireNonNull(skillId, "skillId is required");
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public Long getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TechnicianSkillId that)) {
            return false;
        }
        return Objects.equals(technicianId, that.technicianId)
                && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(technicianId, skillId);
    }
}
