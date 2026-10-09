package com.nexora.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query("""
            select tsa from TechnicianServiceArea tsa
            join fetch tsa.location l
            where tsa.technician.technicianId = :technicianId
            order by l.name asc, l.code asc
            """)
    List<TechnicianServiceArea> findByTechnicianIdWithLocation(@Param("technicianId") Long technicianId);

    @Modifying
    @Query("delete from TechnicianServiceArea tsa where tsa.technician.technicianId = :technicianId")
    void deleteByTechnicianId(@Param("technicianId") Long technicianId);
}

