package com.nexora.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.entity.TechnicianSkillId;

public interface TechnicianSkillRepository
        extends JpaRepository<TechnicianSkill, TechnicianSkillId> {
}
