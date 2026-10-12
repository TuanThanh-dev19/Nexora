package com.nexora.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.common.exception.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProblemHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointWritesAStableProblemResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemAuthenticationEntryPoint(objectMapper).commence(
                request,
                response,
                new InsufficientAuthenticationException("JWT parser detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(body.path("type").asText()).isEqualTo(ProblemTypes.UNAUTHENTICATED.toString());
        assertThat(body.path("detail").asText()).doesNotContain("JWT");
    }

    @Test
    void accessDeniedHandlerWritesAStableProblemResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ProblemAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("internal permission detail"));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(body.path("type").asText()).isEqualTo(ProblemTypes.ACCESS_DENIED.toString());
        assertThat(body.path("detail").asText()).doesNotContain("internal");
    }
}
