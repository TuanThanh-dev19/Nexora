package com.nexora.user.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.user.dto.TechnicianProfileResponse;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.TechnicianSkillsUpdateRequest;
import com.nexora.user.dto.TechnicianUpdateRequest;
import com.nexora.user.service.TechnicianService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/technicians")
@Tag(name = "Technicians", description = "Technician capacity, skill, and service area configuration APIs")
@SecurityRequirement(name = "bearerAuth")
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping("/{technicianId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'TECHNICIAN')")
    @Operation(summary = "Get technician profile details")
    public TechnicianProfileResponse getById(
            @PathVariable Long technicianId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        Long currentUserId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        boolean isManagerOrAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_MANAGER".equals(a.getAuthority()));

        return technicianService.getProfile(technicianId, currentUserId, isManagerOrAdmin);
    }

    @PutMapping("/{technicianId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update technician capacity and availability")
    public TechnicianProfileResponse update(
            @PathVariable Long technicianId,
            @Valid @RequestBody TechnicianUpdateRequest request) {
        return technicianService.updateProfile(technicianId, request);
    }

    @PutMapping("/{technicianId}/skills")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update technician skills and proficiency levels")
    public TechnicianProfileResponse updateSkills(
            @PathVariable Long technicianId,
            @Valid @RequestBody TechnicianSkillsUpdateRequest request) {
        return technicianService.updateSkills(technicianId, request);
    }

    @PutMapping("/{technicianId}/service-areas")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Update technician service areas")
    public TechnicianProfileResponse updateServiceAreas(
            @PathVariable Long technicianId,
            @Valid @RequestBody TechnicianServiceAreasUpdateRequest request) {
        return technicianService.updateServiceAreas(technicianId, request);
    }
}
