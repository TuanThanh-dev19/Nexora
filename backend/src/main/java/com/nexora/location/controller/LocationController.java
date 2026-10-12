package com.nexora.location.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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

import com.nexora.asset.dto.EligibleAssetResponse;
import com.nexora.asset.service.AssetService;
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.dto.LocationCreateRequest;
import com.nexora.location.dto.LocationResponse;
import com.nexora.location.dto.LocationUpdateRequest;
import com.nexora.location.service.LocationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final AssetService assetService;

    public LocationController(LocationService locationService, AssetService assetService) {
        this.locationService = locationService;
        this.assetService = assetService;
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

    /**
     * Lists assets eligible for ticket reporting at the specified location (Issue #30).
     * Accessible to all authenticated users.
     */
    @GetMapping(value = "/api/v1/locations/{locationId}/eligible-assets", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get eligible assets by location",
            description = "Returns active or under-maintenance assets at the exact location for ticket reporting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of eligible assets"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or unsupported equipment type",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Location or equipment type not found",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public PageResponse<EligibleAssetResponse> getEligibleAssets(
            @Parameter(description = "ID of the location", required = true)
            @PathVariable Long locationId,
            @Parameter(description = "Zero-based page index", schema = @Schema(defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Optional equipment type ID filter")
            @RequestParam(required = false) Long equipmentTypeId,
            @Parameter(description = "Optional search query by asset code or name/description")
            @RequestParam(required = false) String query) {
        return assetService.getEligibleAssets(locationId, page, size, equipmentTypeId, query);
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
