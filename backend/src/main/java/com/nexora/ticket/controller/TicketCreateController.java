package com.nexora.ticket.controller;

import com.nexora.ticket.dto.request.CreateTicketRequest;
import com.nexora.ticket.dto.response.CreateTicketResponse;
import com.nexora.ticket.service.TicketCreateService;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets")
@SecurityRequirement(name = "bearerAuth")
public class TicketCreateController {
    private final TicketMultipartReader multipart;
    private final TicketCreateService service;

    public TicketCreateController(TicketMultipartReader multipart, TicketCreateService service) {
        this.multipart = multipart;
        this.service = service;
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Submit a ticket as the current ACTIVE requester",
            description =
                    "Exactly one application/json ticket part; zero to five repeated JPEG/PNG files"
                        + " parts in fileIndex order. Each image is 1–5242880 bytes, total request"
                        + " at most 30 MiB. Server owns reporter, number, status, priority, skill"
                        + " and SLA. Unknown JSON properties are rejected. Location is always"
                        + " required. No automatic retry/idempotency key.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = MultipartSchema.class),
                            encoding =
                                    @Encoding(
                                            name = "ticket",
                                            contentType = MediaType.APPLICATION_JSON_VALUE)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Persisted SUBMITTED ticket, version 0",
                headers = @Header(name = "Location", schema = @Schema(type = "string")),
                content = @Content(schema = @Schema(implementation = CreateTicketResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid JSON, reference state, value, file or attachment mapping",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Invalid/inactive identity",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Current user lacks REQUESTER",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Referenced record not found",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "form-version-changed or concurrent reference conflict",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "413",
                description = "File/request exceeds size limit",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "415",
                description = "Unsupported request, ticket part or image MIME",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "422",
                description =
                        "form-configuration-incompatible (including legacy IMAGE/DATETIME rules)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CreateTicketResponse> create(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request)
            throws IOException, ServletException {
        var data = multipart.read(request);
        var created = service.create(jwt, data.ticket(), data.files());
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
    }

    public record MultipartSchema(
            CreateTicketRequest ticket,
            @ArraySchema(schema = @Schema(type = "string", format = "binary"), maxItems = 5)
                    List<String> files) {}
}
