package com.nexora.notification.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.persistence.SqlReferenceValidator;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final SqlReferenceValidator referenceValidator;

    public NotificationService(NotificationRepository repository,
            SqlReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    public NotificationDocument create(Long recipientId, Long ticketId, String type,
            String title, String message) {
        referenceValidator.requireUser(recipientId);
        if (ticketId != null) {
            referenceValidator.requireTicket(ticketId);
        }

        return repository.save(new NotificationDocument(
                null, null, recipientId, ticketId, type, title, message, false, null, Instant.now()));
    }

    public NotificationDocument markRead(String notificationId) {
        NotificationDocument notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification " + notificationId + " does not exist"));
        notification.markRead(Instant.now());
        return repository.save(notification);
    }
}
