package com.nexora.ticket.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.nexora.ticket.dto.response.TicketDetailResponse.AttachmentMetadata;
import com.nexora.ticket.service.TicketAttachmentUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
@SecurityRequirement(name = "bearerAuth")
public class TicketAttachmentUploadController {
    private final TicketMultipartReader multipart;
    private final TicketAttachmentUploadService service;

    public TicketAttachmentUploadController(TicketMultipartReader multipart, TicketAttachmentUploadService service) {
        this.multipart = multipart;
        this.service = service;
    }

    @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload optional result images as the current assigned technician",
            description = "ACTIVE TECHNICIAN and current assignment required; Ticket must be IN_PROGRESS. "
                    + "Exactly one metadata JSON string {\"attachmentType\":\"WORK_RESULT\"} and 1–5 files. "
                    + "JPEG/PNG only, 1–5242880 bytes each; request at most 30 MiB. "
                    + "Unknown metadata/parts are rejected. All images save or all roll back. "
                    + "Ticket status/version and resolution fields are unchanged. "
                    + "Errors: 400 invalid input, 401 invalid identity, 403 visible but unauthorized, "
                    + "404 unavailable Ticket, 413 size limit, 415 unsupported media, 422 wrong state, 500 storage/persistence.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = MultipartSchema.class)))
    @ApiResponse(responseCode = "201", description = "New attachment metadata, without storage keys or public URLs",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AttachmentMetadata.class))))
    public List<AttachmentMetadata> upload(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId, HttpServletRequest request) throws IOException, ServletException {
        return service.upload(jwt, ticketId, multipart.readWorkResult(request));
    }

    public record MultipartSchema(
            @Schema(example = "{\"attachmentType\":\"WORK_RESULT\"}") String metadata,
            @ArraySchema(schema = @Schema(type = "string", format = "binary"), minItems = 1, maxItems = 5)
                    List<String> files) {}
}
