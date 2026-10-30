package com.nexora.report.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.report.dto.TicketReportExportQuery;
import com.nexora.report.dto.TicketReportQuery;
import com.nexora.report.dto.TicketReportResponse;
import com.nexora.report.service.TicketReportService;
import com.nexora.report.service.TicketReportService.ExportResult;

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
@RequestMapping("/api/v1/reports/tickets")
@Tag(name = "Reports", description = "Ticket period reporting and CSV export APIs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "Insufficient role permissions", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "422", description = "Business rule violation", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class TicketReportController {

    private final TicketReportService reportService;

    public TicketReportController(TicketReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate Ticket period report", description = "Manager and Admin only. fromDate and toDate "
            + "are required (Asia/Bangkok, maximum 92 days inclusive). Optional filters: locationId, priority. "
            + "Returns aggregate summary over the entire filtered scope and paginated ticket details sorted by createdAt DESC, id ASC.")
    @Parameters({
            @Parameter(name = "fromDate", required = true, schema = @Schema(type = "string", format = "date", example = "2026-10-01")),
            @Parameter(name = "toDate", required = true, schema = @Schema(type = "string", format = "date", example = "2026-10-07")),
            @Parameter(name = "locationId", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "priority", schema = @Schema(implementation = com.nexora.common.domain.Priority.class)),
            @Parameter(name = "page", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
            @Parameter(name = "size", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
    })
    public TicketReportResponse report(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        return reportService.getReport(TicketReportQuery.from(parameters));
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    @Operation(summary = "Export Ticket period report to CSV", description = "Manager and Admin only. fromDate and toDate "
            + "are required (Asia/Bangkok, maximum 92 days inclusive). Optional filters: locationId, priority. "
            + "Exports all matching tickets (maximum 10,000) with UTF-8 BOM, formula deactivation, and timezone-aware filename.")
    @Parameters({
            @Parameter(name = "fromDate", required = true, schema = @Schema(type = "string", format = "date", example = "2026-10-01")),
            @Parameter(name = "toDate", required = true, schema = @Schema(type = "string", format = "date", example = "2026-10-07")),
            @Parameter(name = "locationId", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
            @Parameter(name = "priority", schema = @Schema(implementation = com.nexora.common.domain.Priority.class))
    })
    public ResponseEntity<byte[]> export(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        ExportResult result = reportService.exportReport(TicketReportExportQuery.from(parameters));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(result.content());
    }
}

