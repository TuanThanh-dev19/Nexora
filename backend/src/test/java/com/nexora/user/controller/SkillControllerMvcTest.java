package com.nexora.user.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;
import com.nexora.user.dto.SkillResponse;
import com.nexora.user.service.SkillService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SkillController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class SkillControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillService skillService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Anonymous users cannot access skills API (401 Unauthorized)")
    void anonymous_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/skills?active=true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated users with any role can access active skills (200 OK)")
    void authenticatedUser_canAccessSkills() throws Exception {
        SkillResponse skill = new SkillResponse(1L, "ELECTRIC", "Sửa chữa điện", true);
        given(skillService.getActiveSkills(eq(true))).willReturn(List.of(skill));

        mockMvc.perform(get("/api/v1/skills?active=true")
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("ELECTRIC"))
                .andExpect(jsonPath("$[0].name").value("Sửa chữa điện"))
                .andExpect(jsonPath("$[0].active").value(true));
    }
}
