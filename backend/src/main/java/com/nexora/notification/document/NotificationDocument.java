package com.nexora.notification.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

@org.springframework.data.mongodb.core.mapping.Document("notifications")
@CompoundIndex(name = "idx_notification_recipient_read_time",
        def = "{'recipientId': 1, 'read': 1, 'createdAt': -1}")
public class NotificationDocument {

    @Id
    private String id;

    @Indexed(name = "uq_notification_legacy_sql_id", unique = true, sparse = true)
    private Long legacySqlId;

    private Long recipientId;
    private Long ticketId;
    private String notificationType;
    private String title;
    private String message;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;

    protected NotificationDocument() {
    }

    public NotificationDocument(String id, Long legacySqlId, Long recipientId, Long ticketId,
            String notificationType, String title, String message, boolean read,
            Instant readAt, Instant createdAt) {
        this.id = id;
        this.legacySqlId = legacySqlId;
        this.recipientId = recipientId;
        this.ticketId = ticketId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.read = read;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public void markRead(Instant time) {
        this.read = true;
        this.readAt = time;
    }

    public String getId() { return id; }
    public Long getLegacySqlId() { return legacySqlId; }
    public Long getRecipientId() { return recipientId; }
    public Long getTicketId() { return ticketId; }
    public String getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
