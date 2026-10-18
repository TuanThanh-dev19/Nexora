package com.nexora.dashboard.controller;

import java.net.URI;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.nexora.common.exception.ApiProblemDetails;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail queryFailure(DataAccessException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.INTERNAL_SERVER_ERROR,
                URI.create("https://nexora.app/problems/internal-error"), "Internal server error",
                "Dashboard metrics could not be loaded.", request);
    }
}
