package com.nexora.user.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

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
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.pagination.PageResponse;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;
import com.nexora.user.dto.UserCreateRequest;
import com.nexora.user.dto.UserResponse;
import com.nexora.user.dto.UserRolesUpdateRequest;
import com.nexora.user.dto.UserStatusUpdateRequest;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.service.AdminUserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class AdminUserControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserResponse sampleUserResponse() {
        return new UserResponse(
                10L,
                "tech@nexora.local",
                "Trần Kỹ Thuật",
                UserStatus.ACTIVE,
                List.of(RoleCode.TECHNICIAN),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                0L
        );
    }

    @Test
    @DisplayName("Anonymous access is rejected with 401 Unauthorized")
    void anonymous_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Non-ADMIN roles (MANAGER, TECHNICIAN, REQUESTER) are forbidden (403 Forbidden)")
    void nonAdmin_forbidden() throws Exception {
        // Manager
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden());

        // Technician
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.subject("2").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
                .andExpect(status().isForbidden());

        // Requester
        mockMvc.perform(get("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.subject("3").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN role can list users with pagination (200 OK)")
    void admin_listUsers_success() throws Exception {
        PageResponse<UserResponse> pageResponse = new PageResponse<>(
                List.of(sampleUserResponse()), 0, 20, 1, 1);
        given(adminUserService.listUsers(any())).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/users?page=0&size=20")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].email").value("tech@nexora.local"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN role can create user (201 Created with Location header)")
    void admin_createUser_success() throws Exception {
        UserCreateRequest request = new UserCreateRequest(
                "tech@nexora.local",
                "Password123!",
                "Trần Kỹ Thuật",
                UserStatus.ACTIVE,
                Set.of(RoleCode.TECHNICIAN)
        );

        given(adminUserService.createUser(any(), eq(1L))).willReturn(sampleUserResponse());

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/users/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("tech@nexora.local"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN role can get user details by ID (200 OK)")
    void admin_getUserById_success() throws Exception {
        given(adminUserService.getUserById(10L)).willReturn(sampleUserResponse());

        mockMvc.perform(get("/api/v1/admin/users/10")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("ADMIN role can update user status (200 OK)")
    void admin_updateStatus_success() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.LOCKED, 0L);
        UserResponse updated = new UserResponse(
                10L, "tech@nexora.local", "Trần Kỹ Thuật", UserStatus.LOCKED,
                List.of(RoleCode.TECHNICIAN), OffsetDateTime.now(), OffsetDateTime.now(), 1L);

        given(adminUserService.updateUserStatus(eq(10L), any())).willReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/users/10/status")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("ADMIN role can update user roles (200 OK)")
    void admin_updateRoles_success() throws Exception {
        UserRolesUpdateRequest request = new UserRolesUpdateRequest(Set.of(RoleCode.TECHNICIAN, RoleCode.REQUESTER));
        given(adminUserService.updateUserRoles(eq(10L), any(), eq(1L))).willReturn(sampleUserResponse());

        mockMvc.perform(put("/api/v1/admin/users/10/roles")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Validation fails when required fields are missing on create user (400 Bad Request)")
    void createUser_validationFails() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }
}
