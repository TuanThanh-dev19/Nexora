package com.nexora.asset.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nexora.asset.entity.EquipmentType;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {

    @Query("""
            select equipmentType from EquipmentType equipmentType
            where equipmentType.active = true
            order by equipmentType.name asc, equipmentType.code asc, equipmentType.id asc
            """)
    List<EquipmentType> findActiveOrderByNameAndCode();

    Optional<EquipmentType> findByCode(String code);

    @Query("""
            select equipmentType from EquipmentType equipmentType
            order by equipmentType.name asc, equipmentType.code asc, equipmentType.id asc
            """)
    List<EquipmentType> findAllOrderByNameAndCode();
}
