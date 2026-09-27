package com.nexora.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.NOT_FOUND, ProblemTypes.RESOURCE_NOT_FOUND,
                "Resource not found", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.UNPROCESSABLE_ENTITY, ProblemTypes.BUSINESS_RULE_VIOLATION,
                "Business rule rejected", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED,
                "Validation failed", "One or more request fields are invalid.", request);
        detail.setProperty("fieldErrors", fieldErrors);
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMalformedRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed request", "The request body is missing or cannot be parsed.", request);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    ProblemDetail handleInvalidPagination(InvalidPaginationException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.INVALID_PAGINATION,
                "Invalid pagination", exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedSortException.class)
    ProblemDetail handleUnsupportedSort(UnsupportedSortException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.UNSUPPORTED_SORT,
                "Unsupported sort", exception.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedFilterException.class)
    ProblemDetail handleUnsupportedFilter(UnsupportedFilterException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.UNSUPPORTED_FILTER,
                "Unsupported filter", exception.getMessage(), request);
    }

    @ExceptionHandler({StaleWriteException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleStaleWrite(RuntimeException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.CONFLICT, ProblemTypes.STALE_WRITE,
                "Concurrent update conflict",
                "The resource was changed by another request. Reload it and retry your update.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHENTICATED,
                "Authentication required", "Authentication is required to access this resource.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.FORBIDDEN, ProblemTypes.ACCESS_DENIED,
                "Access denied", "You do not have permission to access this resource.", request);
    }
}
