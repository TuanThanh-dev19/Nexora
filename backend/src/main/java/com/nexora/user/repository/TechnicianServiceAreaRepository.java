package com.nexora.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianServiceAreaId;

public interface TechnicianServiceAreaRepository
        extends JpaRepository<TechnicianServiceArea, TechnicianServiceAreaId> {

    @Query("""
            select count(serviceArea) > 0
            from TechnicianServiceArea serviceArea
            where serviceArea.technician.technicianId = :technicianId
              and serviceArea.location.id = :locationId
            """)
    boolean existsDirectCoverage(
            @Param("technicianId") Long technicianId,
            @Param("locationId") Long locationId);
}
