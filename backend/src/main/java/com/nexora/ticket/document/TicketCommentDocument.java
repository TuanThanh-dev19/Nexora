package com.nexora.ticket.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

@org.springframework.data.mongodb.core.mapping.Document("ticket_comments")
@CompoundIndex(name = "idx_comment_ticket_time",
        def = "{'ticketId': 1, 'createdAt': 1}")
public class TicketCommentDocument {

    @Id
    private String id;

    @Indexed(name = "uq_comment_legacy_sql_id", unique = true, sparse = true)
    private Long legacySqlId;

    private Long ticketId;
    private Long authorId;
    private String body;
    private String visibility;
    private Instant createdAt;
    private Instant editedAt;

    protected TicketCommentDocument() {
    }

    public TicketCommentDocument(String id, Long legacySqlId, Long ticketId, Long authorId,
            String body, String visibility, Instant createdAt, Instant editedAt) {
        this.id = id;
        this.legacySqlId = legacySqlId;
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.body = body;
        this.visibility = visibility;
        this.createdAt = createdAt;
        this.editedAt = editedAt;
    }

    public String getId() { return id; }
    public Long getLegacySqlId() { return legacySqlId; }
    public Long getTicketId() { return ticketId; }
    public Long getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public String getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
}
