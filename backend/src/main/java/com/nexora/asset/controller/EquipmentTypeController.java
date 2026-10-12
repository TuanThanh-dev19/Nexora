package com.nexora.asset.controller;

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

import com.nexora.asset.dto.EquipmentTypeCreateRequest;
import com.nexora.asset.dto.EquipmentTypeResponse;
import com.nexora.asset.dto.EquipmentTypeUpdateRequest;
import com.nexora.asset.service.EquipmentTypeService;

import jakarta.validation.Valid;

/**
 * REST controller for EquipmentType reference data.
 *
 * <ul>
 *   <li>GET endpoint is accessible to any authenticated user.</li>
 *   <li>POST/PUT endpoints require the ADMIN role.</li>
 * </ul>
 */
@RestController
public class EquipmentTypeController {

    private final EquipmentTypeService equipmentTypeService;

    public EquipmentTypeController(EquipmentTypeService equipmentTypeService) {
        this.equipmentTypeService = equipmentTypeService;
    }

    // =========================================================================
    // READ — authenticated users
    // =========================================================================

    /**
     * Lists equipment types.
     * When {@code active=true} (default) only active records are returned.
     */
    @GetMapping("/api/v1/equipment-types")
    public List<EquipmentTypeResponse> list(
            @RequestParam(defaultValue = "true") boolean active) {
        return equipmentTypeService.list(active);
    }

    // =========================================================================
    // WRITE — ADMIN only
    // =========================================================================

    /**
     * Creates a new EquipmentType. Requires ADMIN role.
     *
     * @return 201 Created with the resource URI in the Location header
     */
    @PostMapping("/api/v1/admin/equipment-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipmentTypeResponse> create(
            @RequestBody @Valid EquipmentTypeCreateRequest request) {
        EquipmentTypeResponse created = equipmentTypeService.create(request);
        URI uri = URI.create("/api/v1/equipment-types/" + created.id());
        return ResponseEntity.created(uri).body(created);
    }

    /**
     * Updates an existing EquipmentType. Requires ADMIN role.
     */
    @PutMapping("/api/v1/admin/equipment-types/{equipmentTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public EquipmentTypeResponse update(
            @PathVariable Long equipmentTypeId,
            @RequestBody @Valid EquipmentTypeUpdateRequest request) {
        return equipmentTypeService.update(equipmentTypeId, request);
    }
}
