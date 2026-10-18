package com.nexora.ticket.controller;

import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.ticket.service.TicketAttachmentContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Positive;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class TicketAttachmentContentController {
    private final TicketAttachmentContentService service;
    public TicketAttachmentContentController(TicketAttachmentContentService service) { this.service = service; }

    @GetMapping("/api/v1/tickets/{ticketId}/attachments/{attachmentId}/content")
    @Operation(summary = "Read an authorized Ticket image", description = "REPORT_EVIDENCE and WORK_RESULT only. "
            + "Current database roles and historical assignments determine visibility. No query parameters or ranges.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
            @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))}),
        @ApiResponse(responseCode = "400", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "500", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId, @PathVariable @Positive Long attachmentId,
            @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) throw new UnsupportedFilterException("Query parameters are not supported.");
        var image = service.read(jwt, ticketId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.bytes().length).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.filename(), StandardCharsets.UTF_8).build().toString())
                .body(image.bytes());
    }
}
