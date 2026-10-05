package com.nexora.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.asset.dto.EquipmentTypeCreateRequest;
import com.nexora.asset.dto.EquipmentTypeResponse;
import com.nexora.asset.service.EquipmentTypeService;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;

@WebMvcTest(controllers = EquipmentTypeController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class EquipmentTypeControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EquipmentTypeService equipmentTypeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymous_cannot_access_admin_api() throws Exception {
        EquipmentTypeCreateRequest request = new EquipmentTypeCreateRequest("E1", "Type", null);
        mockMvc.perform(post("/api/v1/admin/equipment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void non_admin_cannot_access_admin_api() throws Exception {
        EquipmentTypeCreateRequest request = new EquipmentTypeCreateRequest("E1", "Type", null);
        mockMvc.perform(post("/api/v1/admin/equipment-types")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("TECHNICIAN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_access_admin_api() throws Exception {
        EquipmentTypeCreateRequest request = new EquipmentTypeCreateRequest("E1", "Type", null);
        EquipmentTypeResponse response = new EquipmentTypeResponse(1L, "E1", "Type", null, true, null, null);
        
        when(equipmentTypeService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/equipment-types")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN"))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
