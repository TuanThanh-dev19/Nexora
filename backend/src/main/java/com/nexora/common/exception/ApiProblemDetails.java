package com.nexora.common.exception;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblemDetails {

    private ApiProblemDetails() {
    }

    public static ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        return problem;
    }
}
