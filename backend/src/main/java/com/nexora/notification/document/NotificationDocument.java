package com.nexora.notification.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.nexora.notification.domain.NotificationType;

@Document("notifications")
@CompoundIndex(name = "idx_notification_recipient_read_time",
        def = "{'recipientId': 1, 'read': 1, 'createdAt': -1}")
@CompoundIndex(name = "idx_notification_recipient_time",
        def = "{'recipientId': 1, 'createdAt': -1, '_id': -1}")
@CompoundIndex(name = "idx_notification_recipient_read_at",
        def = "{'recipientId': 1, 'readAt': 1, 'createdAt': -1}")
public class NotificationDocument {

    @Id
    private String id;

    @Indexed(name = "uq_notification_legacy_sql_id", unique = true, sparse = true)
    private Long legacySqlId;

    @Indexed(name = "uq_notification_event_key", unique = true, sparse = true)
    private String eventKey;

    private Long recipientId;
    private Long ticketId;
    private NotificationType type;
    private String notificationType;
    private String title;
    private String message;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;

    protected NotificationDocument() {
    }

    public NotificationDocument(String id, Long legacySqlId, Long recipientId, Long ticketId,
            String eventKey, NotificationType type, String title, String message, boolean read,
            Instant readAt, Instant createdAt) {
        this.id = id;
        this.legacySqlId = legacySqlId;
        this.recipientId = recipientId;
        this.ticketId = ticketId;
        this.eventKey = eventKey;
        this.type = type;
        this.notificationType = type != null ? type.name() : null;
        this.title = title;
        this.message = message;
        this.readAt = readAt;
        this.read = readAt != null || read;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public NotificationDocument(String id, Long legacySqlId, Long recipientId, Long ticketId,
            String notificationType, String title, String message, boolean read,
            Instant readAt, Instant createdAt) {
        this(id, legacySqlId, recipientId, ticketId, null, parseType(notificationType),
                title, message, read, readAt, createdAt);
        this.notificationType = notificationType;
    }

    public void markRead(Instant time) {
        if (this.readAt == null) {
            this.readAt = time != null ? time : Instant.now();
            this.read = true;
        }
    }

    private static NotificationType parseType(String typeStr) {
        if (typeStr == null) return null;
        try {
            return NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getId() { return id; }
    public Long getLegacySqlId() { return legacySqlId; }
    public String getEventKey() { return eventKey; }
    public Long getRecipientId() { return recipientId; }
    public Long getTicketId() { return ticketId; }
    public NotificationType getType() { return type; }
    public String getNotificationType() { return notificationType != null ? notificationType : (type != null ? type.name() : null); }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return readAt != null || read; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
