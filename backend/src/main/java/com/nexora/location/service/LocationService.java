package com.nexora.location.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.location.dto.LocationCreateRequest;
import com.nexora.location.dto.LocationResponse;
import com.nexora.location.dto.LocationUpdateRequest;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;

/**
 * Business logic for the Location reference catalog.
 *
 * <p>Hierarchy rules:
 * <pre>
 *   CAMPUS  (no parent)
 *   BUILDING → CAMPUS
 *   FLOOR   → BUILDING
 *   ROOM    → FLOOR
 *   AREA    → FLOOR
 * </pre>
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Unique code (CREATE + UPDATE)</li>
 *   <li>Parent must exist when provided</li>
 *   <li>Parent must match hierarchy</li>
 *   <li>Cycle detection when updating parent</li>
 *   <li>Soft-delete only (active = false)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LocationService {

    /**
     * Maps each LocationType to the only valid parent type.
     * CAMPUS has no parent — it is absent from this map.
     */
    private static final Map<LocationType, LocationType> VALID_PARENT = new EnumMap<>(LocationType.class);

    static {
        VALID_PARENT.put(LocationType.BUILDING, LocationType.CAMPUS);
        VALID_PARENT.put(LocationType.FLOOR, LocationType.BUILDING);
        VALID_PARENT.put(LocationType.ROOM, LocationType.FLOOR);
        VALID_PARENT.put(LocationType.AREA, LocationType.FLOOR);
    }

    /** Types that must have no parent. */
    private static final Set<LocationType> ROOT_TYPES = Set.of(LocationType.CAMPUS);

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Returns direct children of the given parent, filtered by active if requested.
     * When parentId is null, returns root locations (CAMPUS).
     */
    public List<LocationResponse> list(Long parentId, boolean activeOnly) {
        List<Location> locations;
        if (parentId == null) {
            locations = activeOnly
                    ? locationRepository.findActiveRoots()
                    : locationRepository.findAllRoots();
        } else {
            locations = activeOnly
                    ? locationRepository.findActiveDirectChildren(parentId)
                    : locationRepository.findAllDirectChildren(parentId);
        }
        return locations.stream().map(LocationResponse::from).toList();
    }

    /**
     * Returns a single Location by ID.
     *
     * @throws ResourceNotFoundException when not found
     */
    public LocationResponse getById(Long id) {
        Location location = findOrThrow(id);
        return LocationResponse.from(location);
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Creates a new Location after validating all business rules.
     *
     * @throws BusinessRuleException for duplicate code, invalid hierarchy, missing parent
     */
    @Transactional
    public LocationResponse create(LocationCreateRequest request) {
        validateUniqueCodeForCreate(request.code());
        Location parent = resolveAndValidateParent(request.type(), request.parentId(), null);

        Location location = new Location(
                request.code().strip(),
                request.name().strip(),
                request.type(),
                parent);

        if (request.description() != null) {
            location.changeDescription(request.description().strip());
        }

        return LocationResponse.from(locationRepository.save(location));
    }

    /**
     * Updates an existing Location after validating all business rules.
     *
     * @throws ResourceNotFoundException  when the location does not exist
     * @throws BusinessRuleException      for invalid hierarchy or cycle
     */
    @Transactional
    public LocationResponse update(Long id, LocationUpdateRequest request) {
        Location location = findOrThrow(id);
        Location parent = resolveAndValidateParent(location.getType(), request.parentId(), id);

        location.changeName(request.name().strip());
        location.changeDescription(request.description() != null ? request.description().strip() : null);
        location.changeActive(request.active());

        // Update parent only when it actually changed
        Long currentParentId = location.getParent() != null ? location.getParent().getId() : null;
        Long newParentId = parent != null ? parent.getId() : null;
        if (!java.util.Objects.equals(currentParentId, newParentId)) {
            if (parent != null) {
                detectCycle(id, parent);
            }
            location = updateParent(location, parent);
        }

        return LocationResponse.from(locationRepository.save(location));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Location findOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Location not found: id=" + id));
    }

    private void validateUniqueCodeForCreate(String code) {
        if (locationRepository.existsByCode(code.strip())) {
            throw new BusinessRuleException(
                    "Location code already exists: '" + code + "'");
        }
    }

    /**
     * Resolves and validates the parent location.
     *
     * @param type      the type of the location being created/updated
     * @param parentId  the requested parent ID (may be null)
     * @param selfId    the ID of the location being updated (null for create)
     * @return resolved parent entity, or null for CAMPUS
     * @throws BusinessRuleException      when hierarchy is violated
     * @throws ResourceNotFoundException  when parentId references a non-existent location
     */
    private Location resolveAndValidateParent(LocationType type, Long parentId, Long selfId) {
        if (ROOT_TYPES.contains(type)) {
            if (parentId != null) {
                throw new BusinessRuleException(
                        type + " must be a root location (no parent allowed)");
            }
            return null;
        }

        // Non-root types must have a parent
        if (parentId == null) {
            throw new BusinessRuleException(
                    type + " requires a parent location");
        }

        Location parent = locationRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent location not found: id=" + parentId));

        // Validate hierarchy
        LocationType expectedParentType = VALID_PARENT.get(type);
        if (parent.getType() != expectedParentType) {
            throw new BusinessRuleException(
                    type + " must have a parent of type " + expectedParentType
                    + " but got " + parent.getType());
        }

        return parent;
    }

    /**
     * Detects a cycle in the location hierarchy.
     *
     * <p>Traverses the ancestor chain of {@code newParent} upward.
     * If we encounter {@code locationId}, the update would create a cycle.
     *
     * @param locationId the ID of the location being updated
     * @param newParent  the proposed new parent
     * @throws BusinessRuleException when a cycle is detected
     */
    private void detectCycle(Long locationId, Location newParent) {
        Location cursor = newParent;
        while (cursor != null) {
            if (cursor.getId().equals(locationId)) {
                throw new BusinessRuleException(
                        "Updating the parent would create a cycle in the location hierarchy");
            }
            // Load the parent lazily to avoid full tree serialization
            cursor = cursor.getParent() != null
                    ? locationRepository.findById(cursor.getParent().getId()).orElse(null)
                    : null;
        }
    }

    /**
     * Returns a new Location proxy with the updated parent.
     * We rely on the entity's constructor-style package-private setter-free design,
     * so we update the field via a dedicated method added to the entity.
     */
    private Location updateParent(Location location, Location parent) {
        location.changeParent(parent);
        return location;
    }
}
