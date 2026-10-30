package com.nexora.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.domain.NotificationType;
import com.nexora.notification.service.NotificationService;

@WebMvcTest(controllers = NotificationController.class)
@Import({
        GlobalExceptionHandler.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class NotificationControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private AuthService authService;

    private CurrentUserResponse currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new CurrentUserResponse(
                42L, "user@nexora.local", "Test User", "ACTIVE", List.of("TECHNICIAN"));
        when(authService.getCurrentUser(any(Jwt.class))).thenReturn(currentUser);
    }

    @Test
    @DisplayName("GET /api/v1/notifications: Returns 401 when unauthenticated")
    void listUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications: Returns paginated notifications for current user")
    void listReturnsPageResponse() throws Exception {
        Instant now = Instant.parse("2026-10-30T10:15:30Z");
        NotificationDocument doc = new NotificationDocument(
                "notif-1", null, 42L, 100L, "evt-1", NotificationType.TICKET_ASSIGNED,
                "Ticket Assigned", "You were assigned to #T-100", false, null, now);
        PageResponse<NotificationDocument> pageResponse = new PageResponse<>(List.of(doc), 0, 20, 1, 1);

        when(notificationService.getNotifications(eq(42L), eq(0), eq(20), eq(false)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value("notif-1"))
                .andExpect(jsonPath("$.content[0].recipientId").value(42))
                .andExpect(jsonPath("$.content[0].ticketId").value(100))
                .andExpect(jsonPath("$.content[0].type").value("TICKET_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].title").value("Ticket Assigned"))
                .andExpect(jsonPath("$.content[0].read").value(false));

        verify(notificationService).getNotifications(42L, 0, 20, false);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?unreadOnly=true: Passes unreadOnly parameter")
    void listPassesUnreadOnly() throws Exception {
        PageResponse<NotificationDocument> emptyPage = new PageResponse<>(List.of(), 0, 10, 0, 0);
        when(notificationService.getNotifications(eq(42L), eq(0), eq(10), eq(true)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "10")
                        .param("unreadOnly", "true")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(notificationService).getNotifications(42L, 0, 10, true);
    }

    @Test
    @DisplayName("GET /api/v1/notifications: Returns 400 ProblemDetail when pagination is invalid")
    void listReturns400OnInvalidPagination() throws Exception {
        when(notificationService.getNotifications(eq(42L), eq(-1), eq(20), eq(false)))
                .thenThrow(new InvalidPaginationException("Page must be zero or greater."));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "-1")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid pagination"))
                .andExpect(jsonPath("$.detail").value("Page must be zero or greater."));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count: Returns unread count object")
    void unreadCountReturnsCount() throws Exception {
        when(notificationService.getUnreadCount(42L)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(7));

        verify(notificationService).getUnreadCount(42L);
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read: Marks read and returns updated document")
    void markReadSuccess() throws Exception {
        Instant now = Instant.parse("2026-10-30T10:20:00Z");
        NotificationDocument doc = new NotificationDocument(
                "notif-1", null, 42L, 100L, "evt-1", NotificationType.TICKET_ASSIGNED,
                "Title", "Msg", true, now, now.minusSeconds(60));

        when(notificationService.markRead("notif-1", 42L)).thenReturn(doc);

        mockMvc.perform(patch("/api/v1/notifications/notif-1/read")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("notif-1"))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        verify(notificationService).markRead("notif-1", 42L);
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read: Returns 404 when notification does not exist or belongs to another user")
    void markReadNotFoundReturns404() throws Exception {
        when(notificationService.markRead("notif-other", 42L))
                .thenThrow(new ResourceNotFoundException("Notification notif-other does not exist"));

        mockMvc.perform(patch("/api/v1/notifications/notif-other/read")
                        .with(jwt().jwt(builder -> builder.subject("42"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Notification notif-other does not exist"));
    }
}

