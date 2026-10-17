package com.nexora.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.TechnicianProfile;

public interface TechnicianProfileRepository extends JpaRepository<TechnicianProfile, Long> {

    @Query("""
            select distinct technician
            from TechnicianProfile technician
            left join fetch technician.skillAssignments assignment
            left join fetch assignment.skill
            where technician.technicianId = :technicianId
            """)
    Optional<TechnicianProfile> findByIdWithSkills(
            @Param("technicianId") Long technicianId);

    @Query("""
            select distinct technician
            from TechnicianProfile technician
            left join fetch technician.serviceAreas serviceArea
            left join fetch serviceArea.location
            where technician.technicianId = :technicianId
            """)
    Optional<TechnicianProfile> findByIdWithServiceAreas(
            @Param("technicianId") Long technicianId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select technician from TechnicianProfile technician where technician.technicianId = :technicianId")
    Optional<TechnicianProfile> findByIdForUpdate(@Param("technicianId") Long technicianId);
}
