package com.nexora.ticket.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.TechnicianAssignedTicketsQuery;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.service.TicketAssignmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/technicians/me/assigned-tickets")
@Tag(name = "Technicians", description = "Technician work and assignments")
@SecurityRequirement(name = "bearerAuth")
public class TechnicianWorkController {

    private final TicketAssignmentService assignmentService;

    public TechnicianWorkController(TicketAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List currently assigned tickets for the authenticated technician",
            description = "Returns paginated active work for the authenticated user with TECHNICIAN role. "
                    + "Zero-based page (default 0); size 1–100 (default 20). "
                    + "Sort allowlist: createdAt, updatedAt, ticketNumber. One sort only, default createdAt,desc; id ASC breaks ties. "
                    + "Filters combine with AND semantics. Default status includes both ASSIGNED and IN_PROGRESS; "
                    + "if provided, status must be ASSIGNED or IN_PROGRESS. "
                    + "Unknown, duplicate, blank parameters, or invalid numbers/enums return HTTP 400. "
                    + "Only active assignments (endedAt is null) belonging to the authenticated technician are returned. "
                    + "Manager/Admin without TECHNICIAN role receive 403.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Currently assigned tickets in the shared pagination envelope"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid, duplicate, blank, or unsupported query/sort/pagination parameters",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required or invalid identity",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Caller lacks TECHNICIAN role or lacks permission",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @Parameters({
            @Parameter(name = "page", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
            @Parameter(name = "size", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100")),
            @Parameter(name = "sort", schema = @Schema(type = "string", defaultValue = "createdAt,desc")),
            @Parameter(name = "status", schema = @Schema(allowableValues = {"ASSIGNED", "IN_PROGRESS"})),
            @Parameter(name = "priority", schema = @Schema(implementation = com.nexora.common.domain.Priority.class)),
            @Parameter(name = "locationId", schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    })
    public PageResponse<TicketSummaryResponse> assignedTickets(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return assignmentService.assignedTickets(jwt, TechnicianAssignedTicketsQuery.from(parameters));
    }
}
