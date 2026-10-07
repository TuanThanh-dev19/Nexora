package com.nexora.ticket.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.dto.response.TicketDetailResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.service.TicketReadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Read-only, current-user scoped tickets")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "No ticket read role", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class TicketController {
    private final TicketReadService service;
    public TicketController(TicketReadService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List visible tickets", description = "Zero-based page; size 1–100. Sort allowlist: "
            + "createdAt, updatedAt, ticketNumber. One sort only, default createdAt,desc; id ASC breaks ties. "
            + "Filters combine with AND; locationId matches exactly. Page offset must fit a signed 32-bit integer. "
            + "Unknown/duplicate/blank parameters return 400. "
            + "Visibility and total count use current database roles, including historical technician assignments.")
    @ApiResponse(responseCode = "200", description = "Visible tickets in the shared pagination envelope")
    @Parameters({
            @Parameter(name = "page", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
            @Parameter(name = "size", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100")),
            @Parameter(name = "sort", schema = @Schema(type = "string", defaultValue = "createdAt,desc")),
            @Parameter(name = "status", schema = @Schema(implementation = com.nexora.ticket.entity.TicketStatus.class)),
            @Parameter(name = "priority", schema = @Schema(implementation = com.nexora.common.domain.Priority.class)),
            @Parameter(name = "locationId", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    })
    public PageResponse<TicketSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return service.list(jwt, TicketListQuery.from(parameters));
    }

    @GetMapping(value = "/{ticketId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a visible ticket", description = "Stored form, verbatim JSON values, ordered history "
            + "and REPORT_EVIDENCE/WORK_RESULT metadata. No storage keys or file URLs. "
            + "Missing and invisible tickets return the same safe 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Read-only ticket detail"),
            @ApiResponse(responseCode = "404", description = "Ticket unavailable", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long ticketId) {
        return service.detail(jwt, ticketId);
    }
}
