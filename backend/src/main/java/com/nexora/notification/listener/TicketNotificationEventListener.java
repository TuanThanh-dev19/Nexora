package com.nexora.notification.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexora.notification.domain.NotificationType;
import com.nexora.notification.event.TicketAssignedNotificationEvent;
import com.nexora.notification.event.TicketResolvedNotificationEvent;
import com.nexora.notification.service.NotificationService;

@Component
public class TicketNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(TicketNotificationEventListener.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 100L;

    private final NotificationService notificationService;

    public TicketNotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAssigned(TicketAssignedNotificationEvent event) {
        String eventKey = event.eventKey();
        String title = "Phân công phiếu " + event.ticketNumber();
        String message = "Bạn đã được phân công xử lý phiếu #" + event.ticketNumber() + ": " + event.ticketTitle();

        executeWithRetry(eventKey, event.ticketId(), event.technicianId(), NotificationType.TICKET_ASSIGNED, () -> {
            notificationService.create(
                    event.technicianId(),
                    event.ticketId(),
                    eventKey,
                    NotificationType.TICKET_ASSIGNED,
                    title,
                    message);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketResolved(TicketResolvedNotificationEvent event) {
        String eventKey = event.eventKey();
        String title = "Phiếu " + event.ticketNumber() + " đã được giải quyết";
        String message = "Phiếu #" + event.ticketNumber() + " (" + event.ticketTitle() + ") đã được giải quyết.";

        executeWithRetry(eventKey, event.ticketId(), event.reporterId(), NotificationType.TICKET_RESOLVED, () -> {
            notificationService.create(
                    event.reporterId(),
                    event.ticketId(),
                    eventKey,
                    NotificationType.TICKET_RESOLVED,
                    title,
                    message);
        });
    }

    private void executeWithRetry(String eventKey, Long ticketId, Long recipientId, NotificationType type, Runnable action) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            try {
                action.run();
                log.info("Notification successfully processed: eventKey={}, ticketId={}, recipientId={}, type={}, attempt={}/{}",
                        eventKey, ticketId, recipientId, type, attempt, MAX_ATTEMPTS);
                return;
            } catch (DuplicateKeyException ex) {
                log.info("Duplicate notification prevented (idempotent): eventKey={}, ticketId={}, recipientId={}, type={}",
                        eventKey, ticketId, recipientId, type);
                return;
            } catch (RuntimeException ex) {
                log.warn("Failed to persist notification to MongoDB (attempt {}/{}): eventKey={}, ticketId={}, recipientId={}, type={}, error={}",
                        attempt, MAX_ATTEMPTS, eventKey, ticketId, recipientId, type, ex.getMessage());

                if (attempt >= MAX_ATTEMPTS) {
                    log.error("Exhausted retries persisting notification to MongoDB: eventKey={}, ticketId={}, recipientId={}, type={}",
                            eventKey, ticketId, recipientId, type, ex);
                    return; // Do not fail the committed workflow
                }

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Notification retry backoff interrupted: eventKey={}, ticketId={}, recipientId={}",
                            eventKey, ticketId, recipientId);
                    return;
                }
                backoff *= 2;
            }
        }
    }
}

