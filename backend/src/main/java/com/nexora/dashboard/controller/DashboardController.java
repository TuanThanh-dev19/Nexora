package com.nexora.dashboard.controller;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ProblemDetail;
import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.dashboard.application.*;
import com.nexora.dashboard.dto.DashboardSummaryResponse;
import com.nexora.user.entity.RoleCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final AuthService auth;
    private final DashboardSummaryService service;
    public DashboardController(AuthService auth, DashboardSummaryService service) { this.auth = auth; this.service = service; }

    @GetMapping(value = "/api/v1/dashboard/summary", produces = "application/json")
    @Operation(summary = "Read role-scoped Dashboard metrics", description = "No parameters. Current database roles "
            + "define a union of own reports and current/historical assignments; Manager/Admin see all. "
            + "Open statuses: SUBMITTED/UNDER_REVIEW/ASSIGNED/IN_PROGRESS/REOPENED. SLA includes overdue and "
            + "exactly now+2h. Resolved counts use resolvedAt within the Bangkok ISO week, regardless of status. "
            + "One server instant anchors all metrics and generatedAt (+07:00). Empty scope returns zero, not 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardSummaryResponse.class))),
        @ApiResponse(responseCode = "400", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "500", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))})
    public DashboardSummaryResponse summary(@AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) throw new UnsupportedFilterException("Query parameters are not supported.");
        var user = auth.getCurrentUser(jwt);
        var roles = Arrays.stream(RoleCode.values()).filter(role -> user.roles().contains(role.name()))
                .collect(Collectors.toSet());
        return service.getSummary(new DashboardViewer(user.id(), roles));
    }
}
