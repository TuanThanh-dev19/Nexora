package com.nexora.user.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "technician_skills", schema = "dbo")
public class TechnicianSkill {

    public static final int MIN_PROFICIENCY = 1;
    public static final int MAX_PROFICIENCY = 5;

    @EmbeddedId
    private TechnicianSkillId id;

    @MapsId("technicianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private TechnicianProfile technician;

    @MapsId("skillId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Min(MIN_PROFICIENCY)
    @Max(MAX_PROFICIENCY)
    @Column(name = "proficiency_level", nullable = false)
    private Byte proficiencyLevel;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    protected TechnicianSkill() {
    }

    public TechnicianSkill(TechnicianProfile technician, Skill skill, int proficiencyLevel) {
        this(technician, skill, proficiencyLevel, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public TechnicianSkill(
            TechnicianProfile technician,
            Skill skill,
            int proficiencyLevel,
            OffsetDateTime assignedAt) {
        this.technician = Objects.requireNonNull(technician, "technician is required");
        this.skill = Objects.requireNonNull(skill, "skill is required");
        this.id = new TechnicianSkillId(
                Objects.requireNonNull(
                        technician.getTechnicianId(),
                        "technician must be persisted before assigning a skill"),
                Objects.requireNonNull(skill.getId(), "skill must be persisted before assignment"));
        this.proficiencyLevel = validateProficiency(proficiencyLevel);
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt is required");
    }

    public void changeProficiencyLevel(int proficiencyLevel) {
        this.proficiencyLevel = validateProficiency(proficiencyLevel);
    }

    @PrePersist
    void initializeAssignedAt() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    private static byte validateProficiency(int proficiencyLevel) {
        if (proficiencyLevel < MIN_PROFICIENCY || proficiencyLevel > MAX_PROFICIENCY) {
            throw new IllegalArgumentException("proficiencyLevel must be between 1 and 5");
        }
        return (byte) proficiencyLevel;
    }

    public TechnicianSkillId getId() {
        return id;
    }

    public TechnicianProfile getTechnician() {
        return technician;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getProficiencyLevel() {
        return proficiencyLevel;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }
}
