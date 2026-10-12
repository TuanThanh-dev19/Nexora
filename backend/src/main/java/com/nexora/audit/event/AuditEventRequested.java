package com.nexora.audit.event;

import org.bson.Document;

public record AuditEventRequested(
        Long actorId,
        String action,
        String targetType,
        Long targetId,
        Document beforeData,
        Document afterData,
        String reason) {
}
