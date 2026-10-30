package com.nexora.notification.dto;

import java.time.Instant;

import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.domain.NotificationType;

public record NotificationResponse(
        String id,
        Long recipientId,
        Long ticketId,
        String eventKey,
        NotificationType type,
        String title,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(NotificationDocument doc) {
        if (doc == null) return null;
        return new NotificationResponse(
                doc.getId(),
                doc.getRecipientId(),
                doc.getTicketId(),
                doc.getEventKey(),
                doc.getType(),
                doc.getTitle(),
                doc.getMessage(),
                doc.isRead(),
                doc.getReadAt(),
                doc.getCreatedAt()
        );
    }
}

