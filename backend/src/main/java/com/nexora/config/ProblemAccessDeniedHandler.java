package com.nexora.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.common.exception.ApiProblemDetails;
import com.nexora.common.exception.ProblemTypes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiProblemDetails.create(
                HttpStatus.FORBIDDEN,
                ProblemTypes.ACCESS_DENIED,
                "Access denied",
                "You do not have permission to access this resource.",
                request));
    }
}
