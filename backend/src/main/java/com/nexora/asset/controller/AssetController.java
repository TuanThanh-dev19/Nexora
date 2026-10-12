package com.nexora.asset.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.asset.dto.AssetCreateRequest;
import com.nexora.asset.dto.AssetListQuery;
import com.nexora.asset.dto.AssetResponse;
import com.nexora.asset.dto.AssetStatusChangeRequest;
import com.nexora.asset.dto.AssetStatusHistoryResponse;
import com.nexora.asset.dto.AssetUpdateRequest;
import com.nexora.asset.service.AssetService;
import com.nexora.common.pagination.PageResponse;

import jakarta.validation.Valid;

/**
 * REST controller for Asset administration and status lifecycle management.
 *
 * <p>All endpoints are restricted to users with {@code MANAGER} or {@code ADMIN} roles.
 */
@RestController
@RequestMapping("/api/v1/assets")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Lists assets with pagination and optional filtering by locationId and status.
     *
     * <p>Example: {@code GET /api/v1/assets?page=0&size=20&locationId=1&status=ACTIVE}
     */
    @GetMapping
    public PageResponse<AssetResponse> list(@RequestParam MultiValueMap<String, String> queryParams) {
        AssetListQuery query = AssetListQuery.from(queryParams);
        return assetService.list(query);
    }

    /**
     * Retrieves full details of a specific asset.
     */
    @GetMapping("/{assetId}")
    public AssetResponse getById(@PathVariable Long assetId) {
        return assetService.getById(assetId);
    }

    /**
     * Retrieves the audit history of status transitions for a specific asset.
     */
    @GetMapping("/{assetId}/status-history")
    public List<AssetStatusHistoryResponse> getStatusHistory(@PathVariable Long assetId) {
        return assetService.getStatusHistory(assetId);
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Registers a new asset and initializes status history.
     *
     * @return 201 Created with Location header pointing to the created asset
     */
    @PostMapping
    public ResponseEntity<AssetResponse> create(
            @Valid @RequestBody AssetCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        AssetResponse created = assetService.create(request, currentUserId);
        URI locationUri = URI.create("/api/v1/assets/" + created.id());
        return ResponseEntity.created(locationUri).body(created);
    }

    /**
     * Updates editable properties of an existing asset (excluding status).
     * Enforces optimistic locking through {@code request.version()}.
     */
    @PutMapping("/{assetId}")
    public AssetResponse update(
            @PathVariable Long assetId,
            @Valid @RequestBody AssetUpdateRequest request) {
        return assetService.update(assetId, request);
    }

    /**
     * Transitions an asset to a new status and records history within the same transaction.
     * Enforces optimistic locking through {@code request.version()}.
     */
    @PostMapping("/{assetId}/status")
    public AssetResponse changeStatus(
            @PathVariable Long assetId,
            @Valid @RequestBody AssetStatusChangeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        return assetService.changeStatus(assetId, request, currentUserId);
    }
}
