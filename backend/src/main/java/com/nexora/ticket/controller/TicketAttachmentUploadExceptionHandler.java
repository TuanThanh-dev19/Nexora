package com.nexora.ticket.controller;

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nexora.common.exception.ApiProblemDetails;
import com.nexora.common.exception.ProblemTypes;
import com.nexora.common.storage.FileStorageException;
import com.nexora.common.storage.InvalidFileException;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TicketAttachmentUploadController.class)
public class TicketAttachmentUploadExceptionHandler {
    @ExceptionHandler(InvalidFileException.class)
    ProblemDetail invalidFile(InvalidFileException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.getReason()) {
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_MEDIA -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_REQUEST;
        };
        var problem = ApiProblemDetails.create(status, ProblemTypes.VALIDATION_FAILED,
                "Invalid image upload", exception.getMessage(), request);
        problem.setProperty("fieldErrors", Map.of("files", exception.getMessage()));
        return problem;
    }

    @ExceptionHandler({java.io.IOException.class, jakarta.servlet.ServletException.class})
    ProblemDetail multipart(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.BAD_REQUEST, ProblemTypes.MALFORMED_REQUEST,
                "Malformed multipart request", "The multipart request could not be read.", request);
    }

    @ExceptionHandler({FileStorageException.class, DataAccessException.class,
            PersistenceException.class, TransactionException.class})
    ProblemDetail failed(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.BUSINESS_RULE_VIOLATION,
                "Result images could not be saved", "Upload failed and was rolled back.", request);
    }
}
