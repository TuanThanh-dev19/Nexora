package com.nexora.notification.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.common.pagination.PageResponse;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.dto.NotificationResponse;
import com.nexora.notification.dto.UnreadCountResponse;
import com.nexora.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notifications management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Invalid request or pagination parameters", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(
                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
})
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List current user notifications", description = "Returns paginated notifications for the current authenticated user only. Sorts stable descending by createdAt, _id.")
    @ApiResponse(responseCode = "200", description = "Page of notifications")
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page index") @RequestParam(name = "page", defaultValue = "0") Integer page,
            @Parameter(description = "Page size (1-100)") @RequestParam(name = "size", defaultValue = "20") Integer size,
            @Parameter(description = "Filter only unread notifications") @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        CurrentUserResponse viewer = authService.getCurrentUser(jwt);
        PageResponse<NotificationDocument> pageResult = notificationService.getNotifications(viewer.id(), page, size, unreadOnly);
        List<NotificationResponse> content = pageResult.content().stream()
                .map(NotificationResponse::from)
                .toList();

        return new PageResponse<>(content, pageResult.page(), pageResult.size(), pageResult.totalElements(), pageResult.totalPages());
    }

    @GetMapping(value = "/unread-count", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get unread notifications count", description = "Returns the count of unread notifications for the current authenticated user.")
    @ApiResponse(responseCode = "200", description = "Count of unread notifications")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        CurrentUserResponse viewer = authService.getCurrentUser(jwt);
        long count = notificationService.getUnreadCount(viewer.id());
        return new UnreadCountResponse(count);
    }

    @PatchMapping(value = "/{notificationId}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark notification as read", description = "Marks a notification owned by the current user as read. Idempotent. Returns 404 if not found or belongs to another user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "404", description = "Notification not found or inaccessible", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public NotificationResponse markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String notificationId) {
        CurrentUserResponse viewer = authService.getCurrentUser(jwt);
        NotificationDocument document = notificationService.markRead(notificationId, viewer.id());
        return NotificationResponse.from(document);
    }
}

