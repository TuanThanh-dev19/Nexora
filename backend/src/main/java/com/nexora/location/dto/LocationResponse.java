package com.nexora.location.dto;

import java.time.OffsetDateTime;

import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;

/**
 * Immutable response DTO for {@link Location}.
 *
 * <p>parentId is exposed as a plain Long so the response is flat and does NOT
 * trigger recursive entity serialization.
 */
public record LocationResponse(
        Long id,
        String code,
        String name,
        LocationType type,
        Long parentId,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getCode(),
                location.getName(),
                location.getType(),
                location.getParent() != null ? location.getParent().getId() : null,
                location.getDescription(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
