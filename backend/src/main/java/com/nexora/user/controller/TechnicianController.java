package com.nexora.user.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.common.pagination.PageResponse;
import com.nexora.user.dto.TechnicianListQuery;
import com.nexora.user.dto.TechnicianProfileResponse;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.TechnicianSkillsUpdateRequest;
import com.nexora.user.dto.TechnicianSummaryResponse;
import com.nexora.user.dto.TechnicianUpdateRequest;
import com.nexora.user.service.TechnicianService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(
            summary = "List technicians for workforce management",
            description = "Lists real technician profiles with associated user status and availability for Manager/Admin. "
                    + "Zero-based page; size 1–100 (default 20). Page offset must fit a signed 32-bit integer. "
                    + "Filters combine with AND semantics. Deterministic ordering: fullName ASC, technicianId ASC. "
                    + "Users without TECHNICIAN role or without a technician profile are excluded. "
                    + "LOCKED and DISABLED accounts remain visible. "
                    + "Unsupported parameters such as sort, duplicate parameters, empty/whitespace queries, "
                    + "or invalid boolean values return HTTP 400. "
                    + "This endpoint lists technician profiles for workforce management and does not determine ticket assignment eligibility."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Technician profiles in the shared pagination envelope",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed, duplicate, empty, unsupported, or out-of-range query parameters",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required or invalid caller",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied: requires MANAGER or ADMIN role",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @Parameters({
            @Parameter(name = "page", description = "Zero-based page index (must be >= 0)",
                    schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
            @Parameter(name = "size", description = "Page size (between 1 and 100)",
                    schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100")),
            @Parameter(name = "query", description = "Case-insensitive substring search on fullName or email (trimmed, must not be blank if supplied)",
                    schema = @Schema(type = "string")),
            @Parameter(name = "available", description = "Filter by technician availability (true or false)",
                    schema = @Schema(type = "boolean"))
    })
    public PageResponse<TechnicianSummaryResponse> list(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @AuthenticationPrincipal Jwt jwt) {
        TechnicianListQuery query = TechnicianListQuery.from(queryParams);
        return technicianService.listTechnicians(jwt, query);
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
