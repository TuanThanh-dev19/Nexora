package com.nexora.location.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.location.dto.LocationCreateRequest;
import com.nexora.location.dto.LocationResponse;
import com.nexora.location.dto.LocationUpdateRequest;
import com.nexora.location.service.LocationService;

import jakarta.validation.Valid;

/**
 * REST controller for Location reference data.
 *
 * <ul>
 *   <li>GET endpoints are accessible to any authenticated user.</li>
 *   <li>POST/PUT endpoints require the ADMIN role.</li>
 * </ul>
 */
@RestController
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    // =========================================================================
    // READ — authenticated users
    // =========================================================================

    /**
     * Lists locations.
     *
     * <ul>
     *   <li>When {@code parentId} is absent → returns root locations (CAMPUS).</li>
     *   <li>When {@code parentId} is present → returns direct children of that parent.</li>
     *   <li>When {@code active=true} → filters to active records only.</li>
     * </ul>
     *
     * @param parentId optional parent to scope the query
     * @param active   when {@code true} (default) only active records are returned
     */
    @GetMapping("/api/v1/locations")
    public List<LocationResponse> list(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "true") boolean active) {
        return locationService.list(parentId, active);
    }

    /**
     * Returns the detail of a single Location.
     */
    @GetMapping("/api/v1/locations/{locationId}")
    public LocationResponse getById(@PathVariable Long locationId) {
        return locationService.getById(locationId);
    }

    // =========================================================================
    // WRITE — ADMIN only
    // =========================================================================

    /**
     * Creates a new Location. Requires ADMIN role.
     *
     * @return 201 Created with the Location URI in the Location header
     */
    @PostMapping("/api/v1/admin/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocationResponse> create(@RequestBody @Valid LocationCreateRequest request) {
        LocationResponse created = locationService.create(request);
        URI uri = URI.create("/api/v1/locations/" + created.id());
        return ResponseEntity.created(uri).body(created);
    }

    /**
     * Updates an existing Location. Requires ADMIN role.
     */
    @PutMapping("/api/v1/admin/locations/{locationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public LocationResponse update(
            @PathVariable Long locationId,
            @RequestBody @Valid LocationUpdateRequest request) {
        return locationService.update(locationId, request);
    }
}
