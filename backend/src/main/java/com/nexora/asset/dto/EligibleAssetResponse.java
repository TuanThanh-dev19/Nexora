package com.nexora.asset.dto;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;

/**
 * Response DTO representing an asset eligible for ticket reporting at a specific location.
 * Contains only the fields necessary for UI asset selection (Issue #30).
 */
public record EligibleAssetResponse(
        Long id,
        String assetCode,
        EquipmentTypeReference equipmentType,
        LocationReference location,
        AssetStatus status,
        String description
) {
    public record EquipmentTypeReference(Long id, String code, String name) {}

    public record LocationReference(Long id, String code, String name) {}

    public static EligibleAssetResponse from(Asset asset) {
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

        return new EligibleAssetResponse(
                asset.getId(),
                asset.getAssetCode(),
                eqType,
                loc,
                asset.getStatus(),
                asset.getDescription()
        );
    }
}
