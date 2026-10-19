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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.nexora.auth.service.InvalidCredentialsException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.nexora.ticket.dto.request.TicketActionRequestReader;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.nexora.ticket.service.TicketSubmissionException.class)
    ProblemDetail handleTicketSubmission(com.nexora.ticket.service.TicketSubmissionException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemDetails.create(exception.getStatus(), exception.getType(),
                exception.getStatus().getReasonPhrase(), exception.getMessage(), request);
        if (!exception.getFieldErrors().isEmpty()) problem.setProperty("fieldErrors", exception.getFieldErrors());
        return problem;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadLimit(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.PAYLOAD_TOO_LARGE, ProblemTypes.VALIDATION_FAILED,
                "Upload too large", "Maximum file size is 5 MiB and maximum request size is 30 MiB.", request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMedia(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ProblemTypes.VALIDATION_FAILED,
                "Unsupported media type", "The request content type is not supported.", request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    ProblemDetail handleMultipart(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed multipart request", "Supply valid multipart/form-data with a browser-generated boundary.", request);
    }

    @ExceptionHandler(com.nexora.category.service.FormValidationException.class)
    ProblemDetail handleFormValidation(com.nexora.category.service.FormValidationException exception,
            HttpServletRequest request) {
        ProblemDetail problem = ApiProblemDetails.create(HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemTypes.BUSINESS_RULE_VIOLATION, "Form validation failed", exception.getMessage(), request);
        problem.setProperty("fieldErrors", exception.getFieldErrors());
        return problem;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ProblemDetail handleInvalidParameter(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED,
                "Validation failed", "One or more request parameters are invalid.", request);
        Map<String, String> errors = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            errors.put(mismatch.getName(), "Invalid parameter value.");
        } else if (exception instanceof HandlerMethodValidationException validation) {
            validation.getParameterValidationResults().forEach(result -> {
                if (result instanceof org.springframework.validation.method.ParameterErrors fields) {
                    fields.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
                } else {
                    String name = result.getMethodParameter().getParameterName();
                    errors.put(name == null ? "parameter" : name, "Invalid parameter value.");
                }
            });
        }
        problem.setProperty("fieldErrors", errors);
        return problem;
    }

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
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()
                && mapping.getPath().getFirst().getFrom() == TicketActionRequestReader.class) {
            ProblemDetail problem = ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED,
                    "Validation failed", "One or more request fields are invalid.", request);
            problem.setProperty("fieldErrors", Map.of(mapping.getPath().getFirst().getFieldName(), mapping.getOriginalMessage()));
            return problem;
        }
        ProblemDetail problem = ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed request", "The request body is missing or cannot be parsed.", request);
        problem.setProperty("fieldErrors", Map.of("body", "Expected a valid JSON request body."));
        return problem;
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

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        // Trả 401 với thông điệp chung — không phân biệt email không tồn tại,
        // sai mật khẩu, hay tài khoản bị khoá để tránh lộ thông tin.
        return ApiProblemDetails.create(HttpStatus.UNAUTHORIZED, ProblemTypes.INVALID_CREDENTIALS,
                "Unauthorized", "Invalid email or password.", request);
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
