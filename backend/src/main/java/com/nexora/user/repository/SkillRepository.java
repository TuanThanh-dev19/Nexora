package com.nexora.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nexora.user.entity.Skill;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    @Query("""
            select skill from Skill skill
            where skill.active = true
            order by skill.name asc, skill.code asc, skill.id asc
            """)
    List<Skill> findActiveOrderByNameAndCode();
}
