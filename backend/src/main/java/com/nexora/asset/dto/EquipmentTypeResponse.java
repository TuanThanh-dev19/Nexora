package com.nexora.asset.dto;

import java.time.OffsetDateTime;

import com.nexora.asset.entity.EquipmentType;

/**
 * Immutable response DTO for {@link EquipmentType}.
 */
public record EquipmentTypeResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static EquipmentTypeResponse from(EquipmentType equipmentType) {
        return new EquipmentTypeResponse(
                equipmentType.getId(),
                equipmentType.getCode(),
                equipmentType.getName(),
                equipmentType.getDescription(),
                equipmentType.isActive(),
                equipmentType.getCreatedAt(),
                equipmentType.getUpdatedAt());
    }
}
