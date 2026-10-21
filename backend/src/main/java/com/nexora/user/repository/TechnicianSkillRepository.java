package com.nexora.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.entity.TechnicianSkillId;

public interface TechnicianSkillRepository
        extends JpaRepository<TechnicianSkill, TechnicianSkillId> {

    @Query("""
            select ts from TechnicianSkill ts
            join fetch ts.skill s
            where ts.technician.technicianId = :technicianId
            order by s.name asc, s.code asc
            """)
    List<TechnicianSkill> findByTechnicianIdWithSkill(@Param("technicianId") Long technicianId);

    @Query("""
            select ts from TechnicianSkill ts
            join fetch ts.skill s
            where ts.technician.technicianId in :technicianIds
            order by s.name asc, s.code asc
            """)
    List<TechnicianSkill> findByTechnicianIdInWithSkill(@Param("technicianIds") java.util.Collection<Long> technicianIds);

    @Modifying
    @Query("delete from TechnicianSkill ts where ts.technician.technicianId = :technicianId")
    void deleteByTechnicianId(@Param("technicianId") Long technicianId);
}

