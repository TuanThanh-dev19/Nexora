package com.nexora.category;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.nexora.category.controller.*;
import com.nexora.category.service.*;
import com.nexora.common.exception.*;
import com.nexora.config.*;
import com.nexora.auth.security.JwtRoleConverter;

@WebMvcTest({CategoryAdminController.class, CategoryReferenceController.class})
@Import({SecurityConfig.class, JwtRoleConverter.class, ProblemAccessDeniedHandler.class,
        ProblemAuthenticationEntryPoint.class, GlobalExceptionHandler.class, CategoryJsonConfig.class})
class CategoryControllerMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean CategoryService service;
    @MockitoBean JwtDecoder jwtDecoder;

    static final String CATEGORY = """
            {"code":"TEST","name":"Test","assetPolicy":"OPTIONAL","locationRequired":true,"defaultPriority":"NORMAL","active":true}
            """;

    @ParameterizedTest @ValueSource(strings = {"/categories", "/categories/1/published-form", "/sla-policies?active=true", "/admin/categories", "/admin/form-versions/1", "/admin/categories/1/form-versions", "/admin/skills"})
    void anonymousCannotRead(String path) throws Exception {
        mvc.perform(get("/api/v1" + path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"REQUESTER", "TECHNICIAN", "MANAGER"})
    void nonAdminsCannotUseAnyAdminEndpoint(String role) throws Exception {
        var auth = jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
        for (String path : List.of("/categories", "/categories/1/form-versions", "/form-versions/1", "/skills"))
            mvc.perform(get("/api/v1/admin" + path).with(auth)).andExpect(status().isForbidden());
        for (String path : List.of("/categories", "/categories/1/form-versions/drafts", "/form-versions/1/publish", "/form-versions/1/archive"))
            mvc.perform(post("/api/v1/admin" + path).with(auth).contentType(MediaType.APPLICATION_JSON).content(path.equals("/categories") ? CATEGORY : "{\"revision\":0}"))
                    .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/categories/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(CATEGORY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/form-versions/1").with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"minAttachments\":0,\"fields\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void authenticatedRequesterCanReadAllReferenceEndpointsAndCannotExposeInactiveCategories() throws Exception {
        var requester = jwt().authorities(new SimpleGrantedAuthority("ROLE_REQUESTER"));
        mvc.perform(get("/api/v1/categories?active=false").with(requester)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).categories(false);
        mvc.perform(get("/api/v1/categories/1/published-form").with(requester)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sla-policies?active=true").with(requester)).andExpect(status().isOk());
    }

    @Test void adminCanReadCreateUpdatePublishAndArchive() throws Exception {
        var admin = jwt().jwt(j -> j.subject("7")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/v1/admin/categories").with(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/categories").with(admin).contentType(MediaType.APPLICATION_JSON).content(CATEGORY)).andExpect(status().isCreated());
        verify(service).createCategory(any(), eq(7L));
        mvc.perform(put("/api/v1/admin/categories/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(CATEGORY)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/categories/1/form-versions/drafts").with(admin)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/categories/1/form-versions").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/form-versions/1").with(admin)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/form-versions/1").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"minAttachments\":0,\"fields\":[]}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/form-versions/1/publish").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isOk());
        verify(service).publish(1L, 0L, 7L);
        mvc.perform(post("/api/v1/admin/form-versions/1/archive").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isOk());
    }

    @Test void businessErrorsAndStaleWritesHaveStableHttpContracts() throws Exception {
        var admin = jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        when(service.publish(1L, 0, 1L)).thenThrow(new FormValidationException(Map.of("fields[0].options", "Select fields need at least one active option.")));
        mvc.perform(post("/api/v1/admin/form-versions/1/publish").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors['fields[0].options']").value("Select fields need at least one active option."));
        doThrow(new StaleWriteException()).when(service).publish(1L, 0, 1L);
        mvc.perform(post("/api/v1/admin/form-versions/1/publish").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}"))
                .andExpect(status().isConflict());
        when(service.published(99L)).thenThrow(new ResourceNotFoundException("Category not found."));
        mvc.perform(get("/api/v1/categories/99/published-form").with(admin)).andExpect(status().isNotFound());
        when(service.published(1L)).thenThrow(new BusinessRuleException("This category has no published form."));
        mvc.perform(get("/api/v1/categories/1/published-form").with(admin)).andExpect(status().isUnprocessableEntity());
    }

    @Test void malformedDuplicateRulesAndMissingRevisionAreRejected() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/v1/admin/form-versions/1/publish").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.revision").exists());
        mvc.perform(put("/api/v1/admin/form-versions/1").with(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"revision":0,"fields":[{"fieldKey":"test","label":"Test","fieldType":"TEXT","active":true,"displayOrder":0,
                "validationRules":{"maxLength":4,"maxLength":10},"options":[]}]}
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
