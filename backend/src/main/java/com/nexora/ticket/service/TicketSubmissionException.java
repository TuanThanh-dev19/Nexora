package com.nexora.ticket.service;

import com.nexora.common.exception.ProblemTypes;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Map;

/** Submission-specific failures must not change the existing admin form error contract. */
public class TicketSubmissionException extends RuntimeException {
    private final HttpStatus status;
    private final URI type;
    private final Map<String, String> fieldErrors;

    public TicketSubmissionException(
            HttpStatus status, URI type, String detail, Map<String, String> fieldErrors) {
        super(detail);
        this.status = status;
        this.type = type;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public URI getType() {
        return type;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public static TicketSubmissionException invalid(String path, String message) {
        return new TicketSubmissionException(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_FAILED,
                "One or more submission fields are invalid.",
                Map.of(path, message));
    }

    public static TicketSubmissionException stale() {
        return new TicketSubmissionException(
                HttpStatus.CONFLICT,
                URI.create("https://nexora.app/problems/form-version-changed"),
                "The form is no longer current. Reload it before submitting.",
                Map.of("formVersionId", "Reload the current published form."));
    }

    public static TicketSubmissionException incompatible(String path) {
        return new TicketSubmissionException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                URI.create("https://nexora.app/problems/form-configuration-incompatible"),
                "The published form configuration is incompatible with ticket submission.",
                Map.of(path, "Contact an administrator to publish a compatible form."));
    }

    public static TicketSubmissionException media(String path) {
        return new TicketSubmissionException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ProblemTypes.VALIDATION_FAILED,
                "Only application/json ticket data and JPEG/PNG evidence are supported.",
                Map.of(path, "Unsupported MIME type."));
    }

    public static TicketSubmissionException tooLarge(String path) {
        return new TicketSubmissionException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ProblemTypes.VALIDATION_FAILED,
                "Each file must be at most 5 MiB; the request must be at most 30 MiB.",
                Map.of(path, "Upload exceeds size limit."));
    }
}
