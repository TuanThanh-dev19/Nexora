package com.nexora.user.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.user.dto.SkillAssignmentItem;
import com.nexora.user.dto.TechnicianProfileResponse;
import com.nexora.user.dto.TechnicianServiceAreaResponse;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.TechnicianSkillResponse;
import com.nexora.user.dto.TechnicianSkillsUpdateRequest;
import com.nexora.user.dto.TechnicianSummaryResponse;
import com.nexora.user.dto.TechnicianUpdateRequest;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.service.TechnicianService;
import org.springframework.security.authentication.BadCredentialsException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TechnicianController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class TechnicianControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TechnicianService technicianService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private TechnicianProfileResponse sampleProfile() {
        return new TechnicianProfileResponse(
                5L,
                "tech5@nexora.local",
                "Trần Kỹ Thuật",
                true,
                5,
                0L,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(new TechnicianSkillResponse(1L, "ELEC", "Điện", 4, OffsetDateTime.now())),
                List.of(new TechnicianServiceAreaResponse(10L, "BLD_A", "Tòa A"))
        );
    }

    @Test
    @DisplayName("Anonymous users cannot access technician APIs (401 Unauthorized)")
    void anonymous_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/technicians/5"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/technicians/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQUESTER role cannot read or update technician profile (403 Forbidden)")
    void requester_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TechnicianUpdateRequest(true, 5, 0L))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER or ADMIN can read technician profile (200 OK)")
    void managerOrAdmin_canRead_success() throws Exception {
        given(technicianService.getProfile(eq(5L), eq(1L), eq(true))).willReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(5))
                .andExpect(jsonPath("$.skills[0].skillCode").value("ELEC"))
                .andExpect(jsonPath("$.serviceAreas[0].locationCode").value("BLD_A"));
    }

    @Test
    @DisplayName("Technician can read their own profile (200 OK)")
    void technician_selfRead_success() throws Exception {
        given(technicianService.getProfile(eq(5L), eq(5L), eq(false))).willReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("5").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(5));
    }

    @Test
    @DisplayName("Technician reading another technician profile gets 403 Forbidden")
    void technician_readOther_forbidden() throws Exception {
        given(technicianService.getProfile(eq(6L), eq(5L), eq(false)))
                .willThrow(new AccessDeniedException("Technicians can only access their own profile"));

        mockMvc.perform(get("/api/v1/technicians/6")
                        .with(jwt().jwt(j -> j.subject("5").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER or ADMIN can update technician capacity/availability (200 OK)")
    void manager_updateProfile_success() throws Exception {
        TechnicianUpdateRequest request = new TechnicianUpdateRequest(true, 5, 0L);
        given(technicianService.updateProfile(eq(5L), any())).willReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(5));
    }

    @Test
    @DisplayName("MANAGER or ADMIN can update technician skills (200 OK)")
    void manager_updateSkills_success() throws Exception {
        TechnicianSkillsUpdateRequest request = new TechnicianSkillsUpdateRequest(
                List.of(new SkillAssignmentItem(1L, 4)), 0L);
        given(technicianService.updateSkills(eq(5L), any())).willReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/technicians/5/skills")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(5));
    }

    @Test
    @DisplayName("MANAGER or ADMIN can update technician service areas (200 OK)")
    void manager_updateServiceAreas_success() throws Exception {
        TechnicianServiceAreasUpdateRequest request = new TechnicianServiceAreasUpdateRequest(
                List.of(10L, 11L), 0L);
        given(technicianService.updateServiceAreas(eq(5L), any())).willReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/technicians/5/service-areas")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(5));
    }

    @Test
    @DisplayName("Technician cannot configure capacity (403 Forbidden)")
    void technician_cannotUpdate_forbidden() throws Exception {
        TechnicianUpdateRequest request = new TechnicianUpdateRequest(true, 5, 0L);

        mockMvc.perform(put("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("5").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Validation fails when maxActiveTickets <= 0 (400 Bad Request)")
    void updateProfile_validationFails_capacityZero() throws Exception {
        TechnicianUpdateRequest request = new TechnicianUpdateRequest(true, 0, 0L);

        mockMvc.perform(put("/api/v1/technicians/5")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxActiveTickets").exists());
    }

    @Test
    @DisplayName("Validation fails when proficiencyLevel is out of 1..5 range (400 Bad Request)")
    void updateSkills_validationFails_proficiencyOutOfRange() throws Exception {
        TechnicianSkillsUpdateRequest request = new TechnicianSkillsUpdateRequest(
                List.of(new SkillAssignmentItem(1L, 6)), 0L);

        mockMvc.perform(put("/api/v1/technicians/5/skills")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    // --- GET /api/v1/technicians Tests ---

    @Test
    @DisplayName("Anonymous caller cannot list technicians (401 Unauthorized)")
    void list_anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/technicians"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQUESTER caller cannot list technicians (403 Forbidden)")
    void list_requester_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/technicians")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/access-denied"));
    }

    @Test
    @DisplayName("TECHNICIAN caller cannot list workforce technicians (403 Forbidden)")
    void list_technician_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/technicians")
                        .with(jwt().jwt(j -> j.subject("5").claim("roles", List.of("TECHNICIAN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/access-denied"));
    }

    @Test
    @DisplayName("MANAGER caller can list technicians (200 OK)")
    void list_manager_success() throws Exception {
        TechnicianSummaryResponse item = new TechnicianSummaryResponse(
                21L, "Nguyễn An", "an@example.test", UserStatus.ACTIVE, true, 5, 2L);
        PageResponse<TechnicianSummaryResponse> pageResponse =
                new PageResponse<>(List.of(item), 0, 20, 1L, 1);
        given(technicianService.listTechnicians(any(), any())).willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/technicians?page=0&size=20&query=an&available=true")
                        .with(jwt().jwt(j -> j.subject("2").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].technicianId").value(21))
                .andExpect(jsonPath("$.content[0].fullName").value("Nguyễn An"))
                .andExpect(jsonPath("$.content[0].email").value("an@example.test"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].available").value(true))
                .andExpect(jsonPath("$.content[0].maxActiveTickets").value(5))
                .andExpect(jsonPath("$.content[0].version").value(2));
    }

    @Test
    @DisplayName("ADMIN caller can list technicians (200 OK)")
    void list_admin_success() throws Exception {
        PageResponse<TechnicianSummaryResponse> emptyPage =
                new PageResponse<>(List.of(), 0, 20, 0L, 0);
        given(technicianService.listTechnicians(any(), any())).willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/technicians")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("User with both MANAGER and ADMIN roles can list technicians (200 OK)")
    void list_managerAndAdmin_success() throws Exception {
        PageResponse<TechnicianSummaryResponse> emptyPage =
                new PageResponse<>(List.of(), 0, 20, 0L, 0);
        given(technicianService.listTechnicians(any(), any())).willReturn(emptyPage);

        mockMvc.perform(get("/api/v1/technicians")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER", "ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Invalid pagination parameters return 400 Bad Request with ProblemDetail")
    void list_invalidPagination_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/technicians?page=-1")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-pagination"));

        mockMvc.perform(get("/api/v1/technicians?size=0")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-pagination"));

        mockMvc.perform(get("/api/v1/technicians?size=101")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-pagination"));
    }

    @Test
    @DisplayName("Unsupported sort parameter returns 400 Bad Request")
    void list_sortParameter_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/technicians?sort=fullName,asc")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unsupported-sort"));
    }

    @Test
    @DisplayName("Duplicate parameters return 400 Bad Request")
    void list_duplicateParameters_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/technicians?page=0&page=1")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-pagination"));
    }

    @Test
    @DisplayName("Blank or whitespace-only query returns 400 Bad Request")
    void list_blankQuery_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/technicians?query=   ")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unsupported-filter"));
    }

    @Test
    @DisplayName("Invalid available boolean returns 400 Bad Request")
    void list_invalidAvailable_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/technicians?available=notaboolean")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unsupported-filter"));
    }

    @Test
    @DisplayName("Inactive or invalid caller handled according to existing auth rules (401)")
    void list_inactiveCaller_returnsUnauthorized() throws Exception {
        given(technicianService.listTechnicians(any(), any()))
                .willThrow(new InvalidCredentialsException());

        mockMvc.perform(get("/api/v1/technicians")
                        .with(jwt().jwt(j -> j.subject("99").claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/invalid-credentials"));
    }
}

