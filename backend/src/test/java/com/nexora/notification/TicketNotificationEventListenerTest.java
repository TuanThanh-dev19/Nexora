package com.nexora.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.nexora.notification.domain.NotificationType;
import com.nexora.notification.event.TicketAssignedNotificationEvent;
import com.nexora.notification.event.TicketResolvedNotificationEvent;
import com.nexora.notification.listener.TicketNotificationEventListener;
import com.nexora.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class TicketNotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private TicketNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketNotificationEventListener(notificationService);
    }

    @Test
    @DisplayName("onTicketAssigned calls notificationService with correct parameters and stable event key")
    void onTicketAssignedProcessesSuccessfully() {
        TicketAssignedNotificationEvent event = new TicketAssignedNotificationEvent(
                10L, "T-20261030-0001", "AC unit leak", 101L, 50L, 5L);

        listener.onTicketAssigned(event);

        verify(notificationService).create(
                eq(50L),
                eq(10L),
                eq("TICKET_ASSIGNED:10:101"),
                eq(NotificationType.TICKET_ASSIGNED),
                eq("Phân công phiếu T-20261030-0001"),
                eq("Bạn đã được phân công xử lý phiếu #T-20261030-0001: AC unit leak"));
    }

    @Test
    @DisplayName("onTicketResolved calls notificationService with reporter as recipient and stable event key")
    void onTicketResolvedProcessesSuccessfully() {
        TicketResolvedNotificationEvent event = new TicketResolvedNotificationEvent(
                10L, "T-20261030-0001", "AC unit leak", 202L, 88L, 50L, "FIXED", "Replaced valve");

        listener.onTicketResolved(event);

        verify(notificationService).create(
                eq(88L),
                eq(10L),
                eq("TICKET_RESOLVED:10:202"),
                eq(NotificationType.TICKET_RESOLVED),
                eq("Phiếu T-20261030-0001 đã được giải quyết"),
                eq("Phiếu #T-20261030-0001 (AC unit leak) đã được giải quyết."));
    }

    @Test
    @DisplayName("Retry mechanism retries up to 3 times on transient failure and succeeds on subsequent try")
    void retriesOnTransientFailureThenSucceeds() {
        TicketAssignedNotificationEvent event = new TicketAssignedNotificationEvent(
                10L, "T-001", "Test", 1L, 50L, 5L);

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (callCount.incrementAndGet() < 2) {
                throw new RuntimeException("MongoDB connection timeout");
            }
            return null;
        }).when(notificationService).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());

        listener.onTicketAssigned(event);

        assertThat(callCount.get()).isEqualTo(2);
        verify(notificationService, times(2)).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());
    }

    @Test
    @DisplayName("Retry exhausts after 3 attempts without throwing exception to caller")
    void retriesExhaustedDoesNotFailCaller() {
        TicketResolvedNotificationEvent event = new TicketResolvedNotificationEvent(
                10L, "T-001", "Test", 2L, 88L, 50L, "FIXED", "Summary");

        doThrow(new RuntimeException("MongoDB is completely down"))
                .when(notificationService).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());

        // Must not throw exception
        listener.onTicketResolved(event);

        verify(notificationService, times(3)).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());
    }

    @Test
    @DisplayName("DuplicateKeyException is treated as already handled without retries")
    void duplicateKeyExceptionHandledGracefully() {
        TicketAssignedNotificationEvent event = new TicketAssignedNotificationEvent(
                10L, "T-001", "Test", 1L, 50L, 5L);

        doThrow(new DuplicateKeyException("Duplicate event key"))
                .when(notificationService).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());

        listener.onTicketAssigned(event);

        // Stop immediately on DuplicateKeyException
        verify(notificationService, times(1)).create(anyLong(), anyLong(), anyString(), any(NotificationType.class), anyString(), anyString());
    }
}

