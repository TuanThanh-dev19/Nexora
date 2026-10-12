package com.nexora.audit.document;

import java.time.Instant;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

@org.springframework.data.mongodb.core.mapping.Document("audit_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_audit_target_time",
                def = "{'targetType': 1, 'targetId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_actor_time",
                def = "{'actorId': 1, 'createdAt': -1}", sparse = true)
})
public class AuditEvent {

    @Id
    private String id;

    @Indexed(name = "uq_audit_legacy_sql_id", unique = true, sparse = true)
    private Long legacySqlId;

    private Long actorId;
    private String action;
    private String targetType;
    private Long targetId;

    @Field("before")
    private Document beforeData;

    @Field("after")
    private Document afterData;

    private String reason;
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String id, Long legacySqlId, Long actorId, String action,
            String targetType, Long targetId, Document beforeData, Document afterData,
            String reason, Instant createdAt) {
        this.id = id;
        this.legacySqlId = legacySqlId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public Long getLegacySqlId() { return legacySqlId; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public Document getBeforeData() { return beforeData; }
    public Document getAfterData() { return afterData; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
