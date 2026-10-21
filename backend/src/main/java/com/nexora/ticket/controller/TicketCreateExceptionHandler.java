package com.nexora.ticket.controller;

import com.nexora.common.exception.*;
import com.nexora.common.storage.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Scoped so submission conflict/error semantics do not change other module contracts. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TicketCreateController.class)
public class TicketCreateExceptionHandler {
    @ExceptionHandler(jakarta.persistence.PersistenceException.class)
    ProblemDetail database(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.BUSINESS_RULE_VIOLATION,
                "Submission could not be persisted",
                "Submission failed and was rolled back.",
                request);
    }

    @ExceptionHandler({java.io.IOException.class, jakarta.servlet.ServletException.class})
    ProblemDetail multipart(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.MALFORMED_REQUEST,
                "Malformed multipart request",
                "The multipart request could not be read.",
                request);
    }

    @ExceptionHandler({
        org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.dao.PessimisticLockingFailureException.class,
        jakarta.persistence.PessimisticLockException.class,
        jakarta.persistence.LockTimeoutException.class
    })
    ProblemDetail conflict(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(
                HttpStatus.CONFLICT,
                ProblemTypes.STALE_WRITE,
                "Reference conflict",
                "Submission references changed concurrently. Reload the form and check the"
                        + " reference data.",
                request);
    }

    @ExceptionHandler(InvalidFileException.class)
    ProblemDetail invalidFile(Exception exception, HttpServletRequest request) {
        ProblemDetail problem =
                ApiProblemDetails.create(
                        HttpStatus.BAD_REQUEST,
                        ProblemTypes.VALIDATION_FAILED,
                        "Invalid image",
                        "Uploaded image content is invalid.",
                        request);
        problem.setProperty(
                "fieldErrors",
                java.util.Map.of("attachments", "Choose valid JPEG/PNG evidence images."));
        return problem;
    }

    @ExceptionHandler(FileStorageException.class)
    ProblemDetail storage(Exception exception, HttpServletRequest request) {
        return ApiProblemDetails.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.BUSINESS_RULE_VIOLATION,
                "Evidence could not be stored",
                "Submission failed and was rolled back.",
                request);
    }
}
