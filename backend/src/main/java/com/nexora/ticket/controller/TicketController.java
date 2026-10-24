package com.nexora.ticket.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
import com.nexora.ticket.dto.request.ReviewTicketRequest;
import com.nexora.ticket.dto.request.StartTicketRequest;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
import com.nexora.ticket.dto.response.TicketDetailResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.service.TicketAssignmentService;
import com.nexora.ticket.service.TicketReadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Ticket read, workflow, and technician assignment management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Insufficient role permissions", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class TicketController {
    private final TicketReadService readService;
    private final TicketAssignmentService assignmentService;

    public TicketController(TicketReadService readService, TicketAssignmentService assignmentService) {
        this.readService = readService;
        this.assignmentService = assignmentService;
    }

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
        return readService.list(jwt, TicketListQuery.from(parameters));
    }

    @GetMapping(value = "/{ticketId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read a visible ticket", description = "Stored form, verbatim JSON values, ordered history "
            + "and REPORT_EVIDENCE/WORK_RESULT metadata. currentAssignment is the active assignment (endedAt=null), "
            + "or explicitly null when none exists. No storage keys or file URLs. "
            + "Missing and invisible tickets return the same safe 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Read-only ticket detail"),
            @ApiResponse(responseCode = "404", description = "Ticket unavailable", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long ticketId) {
        return readService.detail(jwt, ticketId);
    }

    @GetMapping(value = "/{ticketId}/eligible-technicians", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List eligible and blocked technicians for ticket assignment",
            description = "Evaluates technician eligibility (availability, workload capacity, required skill snapshot, "
                    + "and hierarchical service-area coverage). Read-only; does not reserve capacity or assign the ticket. "
                    + "Requires MANAGER or ADMIN authorization. Missing or inaccessible tickets return 404. "
                    + "Query filters combine with AND. Deterministic sorting by fullName ASC, technicianId ASC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated candidate technicians"),
            @ApiResponse(responseCode = "400", description = "Invalid query or pagination parameters", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Requires MANAGER or ADMIN role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @Parameters({
            @Parameter(name = "page", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
            @Parameter(name = "size", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100")),
            @Parameter(name = "query", schema = @Schema(type = "string", description = "Case-insensitive substring search across full name and email")),
            @Parameter(name = "eligibility", schema = @Schema(type = "string", allowableValues = {"ELIGIBLE", "BLOCKED"},
                    description = "Filter candidate list to only ELIGIBLE or BLOCKED technicians"))
    })
    public PageResponse<EligibleTechnicianResponse> eligibleTechnicians(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return assignmentService.eligibleTechnicians(jwt, ticketId, EligibleTechniciansQuery.from(parameters));
    }

    @PostMapping(value = "/{ticketId}/review", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Begin reviewing a submitted ticket", description = "MANAGER/ADMIN only. Accepts only a required "
            + "nonnegative integer Ticket version. Commits SUBMITTED to UNDER_REVIEW and BEGIN_REVIEW history together. "
            + "Returns the updated version and currentAssignment=null; does not assign a Technician.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review committed"),
            @ApiResponse(responseCode = "404", description = "Ticket unavailable", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale Ticket version; reload and confirm again", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Invalid source state", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketActionResultResponse review(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long ticketId,
            @Valid @RequestBody ReviewTicketRequest request) {
        return assignmentService.review(jwt, ticketId, request);
    }

    @PostMapping(value = "/{ticketId}/assign", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign a technician to a ticket",
            description = "Accepts only required integer version (>=0) and technicianId (>0); no override or unknown fields. "
                    + "Assigns from UNDER_REVIEW or REOPENED. Re-evaluates eligibility within the transaction and serializes capacity. "
                    + "Transitions ticket to ASSIGNED, creates an active assignment record, and appends status history. "
                    + "Requires MANAGER or ADMIN authorization. Returns 409 on version conflict and 422 if the candidate is blocked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket successfully assigned"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Requires MANAGER or ADMIN role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket unavailable or technician user does not exist", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale ticket version conflict", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Technician ineligible or invalid ticket status transition", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketActionResultResponse assign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody AssignTicketRequest request) {
        return assignmentService.assign(jwt, ticketId, request);
    }

    @PostMapping(value = "/{ticketId}/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start working on an assigned ticket",
            description = "Accepts only required integer version (>=0). Only active accounts with TECHNICIAN role who are the current active assignee (endedAt is null) can start. "
                    + "Transitions ticket from ASSIGNED to IN_PROGRESS, sets active assignment acceptedAt to server time, increments version, and appends START_WORK history event. "
                    + "Returns 400 on malformed/unsupported fields, 401 on unauthenticated, 403 on non-technician, 404 on missing/invisible ticket, 409 on version conflict, 422 if not ASSIGNED or caller is not current assignee.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket successfully moved to IN_PROGRESS"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unsupported fields", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Requires TECHNICIAN role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale ticket version conflict", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Invalid ticket status transition or caller not current assignee", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketActionResultResponse start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody StartTicketRequest request) {
        return assignmentService.start(jwt, ticketId, request);
    }

    @GetMapping(value = "/{ticketId}/work-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get ticket work logs",
            description = "Returns a chronological list of work logs (createdAt ASC, id ASC) for a visible ticket. "
                    + "Empty array if no logs exist. Rejects any unexpected query parameters with HTTP 400. "
                    + "Caller must have visibility access to the ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of work logs for the ticket"),
            @ApiResponse(responseCode = "400", description = "Query parameters are not supported", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public java.util.List<com.nexora.ticket.dto.response.WorkLogResponse> workLogs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return assignmentService.workLogs(jwt, ticketId, parameters);
    }

    @PostMapping(value = "/{ticketId}/work-logs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @Operation(summary = "Add a work log to a ticket in progress",
            description = "Creates a work log record for an IN_PROGRESS ticket. Only the current assigned technician can record work logs. "
                    + "Increments ticket version and updates updatedAt timestamp. Returns the created work log and new ticketVersion.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Work log successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unsupported fields", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Requires TECHNICIAN role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale ticket version conflict", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Ticket not in IN_PROGRESS or caller is not current active assignee", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public com.nexora.ticket.dto.response.WorkLogCreatedResponse addWorkLog(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody com.nexora.ticket.dto.request.AddWorkLogRequest request) {
        return assignmentService.addWorkLog(jwt, ticketId, request);
    }

    @PostMapping(value = "/{ticketId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Resolve an in-progress ticket",
            description = "Resolves an IN_PROGRESS ticket. Requires at least one prior persisted work log. "
                    + "Outcome must be FIXED, TEMPORARY_FIX, NO_FAULT_FOUND, or UNRESOLVED (DUPLICATE is rejected). "
                    + "Terminates active assignment, records RESOLVE history, frees capacity, and increments ticket version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket successfully resolved"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unsupported fields", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Requires TECHNICIAN role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale ticket version conflict", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Ticket not in IN_PROGRESS, caller not active assignee, or missing prior work log", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketActionResultResponse resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody com.nexora.ticket.dto.request.ResolveTicketRequest request) {
        return assignmentService.resolve(jwt, ticketId, request);
    }

    @PostMapping(value = "/{ticketId}/close", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Close a resolved ticket",
            description = "Accepts only a required nonnegative integer version and an optional reason (trimmed, at most 500 characters; blank becomes null). "
                    + "Only the ACTIVE reporter with REQUESTER role can close RESOLVED tickets, including mixed-role reporters. No force-close. "
                    + "Checks visibility before permission, version and state. Commits closedAt, version and CLOSE history together, preserving resolution data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket successfully closed"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or unsupported fields", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Visible ticket but caller is not its reporter with REQUESTER role", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Ticket not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Stale ticket version conflict", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Ticket is not RESOLVED or still has an inconsistent active assignment", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TicketActionResultResponse close(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long ticketId,
            @Valid @RequestBody com.nexora.ticket.dto.request.CloseTicketRequest request) {
        return assignmentService.close(jwt, ticketId, request);
    }
}

