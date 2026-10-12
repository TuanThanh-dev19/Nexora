package com.nexora.location.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.asset.dto.EligibleAssetResponse;
import com.nexora.asset.entity.AssetStatus;
import com.nexora.asset.service.AssetService;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;
import com.nexora.location.dto.LocationCreateRequest;
import com.nexora.location.dto.LocationResponse;
import com.nexora.location.dto.LocationUpdateRequest;
import com.nexora.location.entity.LocationType;
import com.nexora.location.service.LocationService;

@WebMvcTest(controllers = LocationController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class LocationControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymous_cannot_access_admin_api() throws Exception {
        LocationCreateRequest request = new LocationCreateRequest("C1", "Campus", LocationType.CAMPUS, null, null);
        mockMvc.perform(post("/api/v1/admin/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void non_admin_cannot_access_admin_api() throws Exception {
        LocationCreateRequest request = new LocationCreateRequest("C1", "Campus", LocationType.CAMPUS, null, null);
        mockMvc.perform(post("/api/v1/admin/locations")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("TECHNICIAN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_access_admin_api() throws Exception {
        LocationCreateRequest request = new LocationCreateRequest("C1", "Campus", LocationType.CAMPUS, null, null);
        LocationResponse response = new LocationResponse(1L, "C1", "Campus", LocationType.CAMPUS, null, null, true, null, null);
        
        when(locationService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/locations")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // Issue #30: Eligible Assets by Location Tests
    // =========================================================================

    @Test
    @DisplayName("Anonymous users cannot access eligible-assets API (401)")
    void eligibleAssets_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/locations/1/eligible-assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unauthenticated"));
    }

    @Test
    @DisplayName("Authenticated Requester can access eligible assets with default parameters (200)")
    void eligibleAssets_authenticatedRequester_success() throws Exception {
        EligibleAssetResponse item = new EligibleAssetResponse(
                101L,
                "AST-001",
                new EligibleAssetResponse.EquipmentTypeReference(10L, "EQ-PROJ", "Projector"),
                new EligibleAssetResponse.LocationReference(1L, "LOC-1", "Room 101"),
                AssetStatus.ACTIVE,
                "Ceiling projector"
        );
        PageResponse<EligibleAssetResponse> pageResponse = new PageResponse<>(List.of(item), 0, 20, 1, 1);

        when(assetService.getEligibleAssets(1L, 0, 20, null, null)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/locations/1/eligible-assets")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value(101))
                .andExpect(jsonPath("$.content[0].assetCode").value("AST-001"))
                .andExpect(jsonPath("$.content[0].equipmentType.code").value("EQ-PROJ"))
                .andExpect(jsonPath("$.content[0].location.code").value("LOC-1"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].description").value("Ceiling projector"))
                // Ensure internal fields are not exposed (BR-7)
                .andExpect(jsonPath("$.content[0].serialNumber").doesNotExist())
                .andExpect(jsonPath("$.content[0].purchasedAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist());
    }

    @Test
    @DisplayName("Query parameters are forwarded correctly to AssetService")
    void eligibleAssets_withQueryParameters_forwardedToService() throws Exception {
        PageResponse<EligibleAssetResponse> emptyPage = new PageResponse<>(List.of(), 1, 10, 0, 0);

        when(assetService.getEligibleAssets(5L, 1, 10, 2L, "AC")).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/locations/5/eligible-assets?page=1&size=10&equipmentTypeId=2&query=AC")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));

        verify(assetService).getEligibleAssets(5L, 1, 10, 2L, "AC");
    }

    @Test
    @DisplayName("Location not found returns 404 ProblemDetail (BR-6)")
    void eligibleAssets_locationNotFound_returnsNotFound() throws Exception {
        when(assetService.getEligibleAssets(999L, 0, 20, null, null))
                .thenThrow(new ResourceNotFoundException("Location not found: id=999"));

        mockMvc.perform(get("/api/v1/locations/999/eligible-assets")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/resource-not-found"))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    @DisplayName("Unsupported or inactive equipment type returns 400 ProblemDetail (BR-4)")
    void eligibleAssets_unsupportedEquipmentType_returnsBadRequest() throws Exception {
        when(assetService.getEligibleAssets(1L, 0, 20, 99L, null))
                .thenThrow(new UnsupportedFilterException("Equipment type is inactive: 99"));

        mockMvc.perform(get("/api/v1/locations/1/eligible-assets?equipmentTypeId=99")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unsupported-filter"))
                .andExpect(jsonPath("$.title").value("Unsupported filter"));
    }

    @Test
    @DisplayName("Invalid pagination parameters returns 400 ProblemDetail (BR-5)")
    void eligibleAssets_invalidPagination_returnsBadRequest() throws Exception {
        when(assetService.getEligibleAssets(eq(1L), anyInt(), anyInt(), any(), any()))
                .thenThrow(new InvalidPaginationException("Page must be zero or greater and size between 1 and 100."));

        mockMvc.perform(get("/api/v1/locations/1/eligible-assets?page=-1")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-pagination"))
                .andExpect(jsonPath("$.title").value("Invalid pagination"));
    }
}

