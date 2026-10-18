package com.nexora.ticket.controller;

import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.nexora.common.storage.FileStorageException;
import com.nexora.common.exception.ApiProblemDetails;

@RestControllerAdvice(assignableTypes = TicketAttachmentContentController.class)
public class AttachmentContentExceptionHandler {
    @ExceptionHandler(FileStorageException.class)
    ProblemDetail storageFailure(FileStorageException exception, HttpServletRequest request) {
        return ApiProblemDetails.create(HttpStatus.INTERNAL_SERVER_ERROR,
                URI.create("https://nexora.app/problems/internal-error"), "Internal server error",
                "The attachment could not be read.", request);
    }
}
