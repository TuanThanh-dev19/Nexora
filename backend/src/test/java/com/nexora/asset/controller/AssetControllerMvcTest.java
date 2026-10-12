package com.nexora.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.asset.dto.AssetCreateRequest;
import com.nexora.asset.dto.AssetResponse;
import com.nexora.asset.dto.AssetStatusChangeRequest;
import com.nexora.asset.dto.AssetStatusHistoryResponse;
import com.nexora.asset.dto.AssetUpdateRequest;
import com.nexora.asset.entity.AssetStatus;
import com.nexora.asset.service.AssetService;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.exception.InvalidStateTransitionException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;

@WebMvcTest(controllers = AssetController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class AssetControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private AssetResponse sampleResponse() {
        return new AssetResponse(
                100L,
                "AST-001",
                "SN-12345",
                new AssetResponse.EquipmentTypeReference(10L, "EQ-PROJ", "Projector"),
                new AssetResponse.LocationReference(20L, "LOC-A101", "Room A101"),
                AssetStatus.ACTIVE,
                "Sample description",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2028, 1, 1),
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    // =========================================================================
    // AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Anonymous users cannot access asset APIs (401 Unauthorized)")
    void anonymous_cannot_access_asset_apis() throws Exception {
        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/assets/100"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQUESTER role is forbidden from managing assets (403 Forbidden)")
    void requester_cannot_access_asset_apis() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .with(jwt().jwt(j -> j.subject("2").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TECHNICIAN role is forbidden from managing assets (403 Forbidden)")
    void technician_cannot_access_asset_apis() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .with(jwt().jwt(j -> j.subject("3").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER role can access asset APIs")
    void manager_can_access_asset_apis() throws Exception {
        when(assetService.list(any())).thenReturn(new PageResponse<>(List.of(sampleResponse()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/assets")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].assetCode").value("AST-001"));
    }

    @Test
    @DisplayName("ADMIN role can access asset APIs")
    void admin_can_access_asset_apis() throws Exception {
        when(assetService.getById(100L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/assets/100")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.assetCode").value("AST-001"));
    }

    // =========================================================================
    // CRUD & VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/assets creates asset and returns 201 Created with Location header")
    void create_validRequest_returns201() throws Exception {
        AssetCreateRequest request = new AssetCreateRequest(
                "AST-001", 10L, 20L, "SN-12345", "New asset", null, null
        );
        when(assetService.create(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/assets/100"))
                .andExpect(jsonPath("$.assetCode").value("AST-001"));
    }

    @Test
    @DisplayName("POST /api/v1/assets with blank code returns 400 Bad Request")
    void create_blankCode_returns400() throws Exception {
        AssetCreateRequest request = new AssetCreateRequest(
                "", 10L, 20L, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/assets")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/assets/{id} updates asset when valid")
    void update_validRequest_returns200() throws Exception {
        AssetUpdateRequest request = new AssetUpdateRequest(
                0L, 10L, 20L, "SN-12345", "Updated description", null, null
        );
        when(assetService.update(eq(100L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/assets/100")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L));
    }

    @Test
    @DisplayName("PUT /api/v1/assets/{id} with stale version returns 409 Conflict")
    void update_staleVersion_returns409() throws Exception {
        AssetUpdateRequest request = new AssetUpdateRequest(
                0L, 10L, 20L, null, null, null, null
        );
        when(assetService.update(eq(100L), any())).thenThrow(new StaleWriteException());

        mockMvc.perform(put("/api/v1/assets/100")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // STATUS TRANSITION TESTS
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/assets/{id}/status changes status successfully")
    void changeStatus_validRequest_returns200() throws Exception {
        AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                AssetStatus.UNDER_MAINTENANCE,
                "Thiết bị cần kiểm tra nguồn điện",
                2L
        );
        AssetResponse updated = new AssetResponse(
                100L, "AST-001", "SN-12345", null, null,
                AssetStatus.UNDER_MAINTENANCE, null, null, null, 3L, null, null
        );
        when(assetService.changeStatus(eq(100L), any(), any())).thenReturn(updated);

        mockMvc.perform(post("/api/v1/assets/100/status")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_MAINTENANCE"));
    }

    @Test
    @DisplayName("POST /api/v1/assets/{id}/status with blank reason returns 400 Bad Request")
    void changeStatus_blankReason_returns400() throws Exception {
        AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                AssetStatus.UNDER_MAINTENANCE,
                "   ",
                2L
        );

        mockMvc.perform(post("/api/v1/assets/100/status")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/assets/{id}/status with stale version returns 409 Conflict")
    void changeStatus_staleVersion_returns409() throws Exception {
        AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                AssetStatus.UNDER_MAINTENANCE,
                "Cần bảo trì",
                1L
        );
        when(assetService.changeStatus(eq(100L), any(), any()))
                .thenThrow(new StaleWriteException());

        mockMvc.perform(post("/api/v1/assets/100/status")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/assets/{id}/status to current status returns 422 Unprocessable Entity")
    void changeStatus_sameStatus_returns422() throws Exception {
        AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                AssetStatus.ACTIVE,
                "Không đổi gì cả",
                1L
        );
        when(assetService.changeStatus(eq(100L), any(), any()))
                .thenThrow(new InvalidStateTransitionException("Asset is already in status ACTIVE"));

        mockMvc.perform(post("/api/v1/assets/100/status")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /api/v1/assets/{id}/status-history returns history list")
    void getStatusHistory_success_returns200() throws Exception {
        AssetStatusHistoryResponse historyResponse = new AssetStatusHistoryResponse(
                1L, 100L, AssetStatus.ACTIVE, AssetStatus.UNDER_MAINTENANCE,
                "Kiểm tra bảo dưỡng định kỳ",
                new AssetStatusHistoryResponse.UserReference(1L, "Manager User", "manager@campusfix.vn"),
                null,
                OffsetDateTime.now()
        );
        when(assetService.getStatusHistory(100L)).thenReturn(List.of(historyResponse));

        mockMvc.perform(get("/api/v1/assets/100/status-history")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].toStatus").value("UNDER_MAINTENANCE"))
                .andExpect(jsonPath("$[0].reason").value("Kiểm tra bảo dưỡng định kỳ"));
    }
}
