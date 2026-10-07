package com.nexora.asset.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;

public record AssetResponse(
        Long id,
        String assetCode,
        String serialNumber,
        EquipmentTypeReference equipmentType,
        LocationReference location,
        AssetStatus status,
        String description,
        LocalDate purchasedAt,
        LocalDate warrantyUntil,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record EquipmentTypeReference(Long id, String code, String name) {}

    public record LocationReference(Long id, String code, String name) {}

    public static AssetResponse from(Asset asset) {
        EquipmentTypeReference eqType = asset.getEquipmentType() != null
                ? new EquipmentTypeReference(
                        asset.getEquipmentType().getId(),
                        asset.getEquipmentType().getCode(),
                        asset.getEquipmentType().getName())
                : null;

        LocationReference loc = asset.getLocation() != null
                ? new LocationReference(
                        asset.getLocation().getId(),
                        asset.getLocation().getCode(),
                        asset.getLocation().getName())
                : null;

        return new AssetResponse(
                asset.getId(),
                asset.getAssetCode(),
                asset.getSerialNumber(),
                eqType,
                loc,
                asset.getStatus(),
                asset.getDescription(),
                asset.getPurchasedAt(),
                asset.getWarrantyUntil(),
                asset.getVersion(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
