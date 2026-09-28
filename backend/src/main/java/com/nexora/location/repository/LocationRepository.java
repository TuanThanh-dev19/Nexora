package com.nexora.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.location.entity.Location;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("""
            select location from Location location
            where location.active = true and location.parent is null
            order by location.name asc, location.code asc, location.id asc
            """)
    List<Location> findActiveRoots();

    @Query("""
            select location from Location location
            where location.active = true and location.parent.id = :parentId
            order by location.name asc, location.code asc, location.id asc
            """)
    List<Location> findActiveDirectChildren(@Param("parentId") Long parentId);

    Optional<Location> findByCode(String code);
}
