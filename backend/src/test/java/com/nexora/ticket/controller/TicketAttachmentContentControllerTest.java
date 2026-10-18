package com.nexora.ticket.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.nexora.config.*;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.*;
import com.nexora.common.storage.FileStorageException;
import com.nexora.ticket.service.TicketAttachmentContentService;

@WebMvcTest(TicketAttachmentContentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AttachmentContentExceptionHandler.class,
        JwtRoleConverter.class, ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class TicketAttachmentContentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketAttachmentContentService service;
    @MockitoBean JwtDecoder decoder;
    final String url = "/api/v1/tickets/10/attachments/20/content";
    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void safeBinaryHeadersAndActualLength() throws Exception {
        when(service.read(any(), eq(10L), eq(20L))).thenReturn(
                new TicketAttachmentContentService.Content(new byte[]{1,2,3}, "image/png", "ảnh.png"));
        mvc.perform(get(url).with(jwt())).andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1,2,3})).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")));
    }
    @ParameterizedTest @ValueSource(strings = {"?token=x", "?download=true", "?path=x"})
    void rejectsAllQueryParameters(String query) throws Exception {
        mvc.perform(get(url + query).with(jwt())).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidIds(String id) throws Exception {
        mvc.perform(get("/api/v1/tickets/" + id + "/attachments/20/content").with(jwt()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void missingAndStorageErrorsAreSafeJsonEvenWithBinaryAccept() throws Exception {
        when(service.read(any(), any(), any())).thenThrow(new ResourceNotFoundException("The requested attachment is unavailable."));
        mvc.perform(get(url).with(jwt()).accept("image/png", "application/problem+json"))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        doThrow(new FileStorageException("secret path", null)).when(service).read(any(), any(), any());
        mvc.perform(get(url).with(jwt())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The attachment could not be read."));
    }
}
