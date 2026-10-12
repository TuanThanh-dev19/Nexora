package com.nexora.audit.service;

import java.time.Instant;

import org.bson.Document;
import org.springframework.stereotype.Service;

import com.nexora.audit.document.AuditEvent;
import com.nexora.audit.repository.AuditEventRepository;
import com.nexora.common.persistence.SqlReferenceValidator;

@Service
public class AuditEventService {

    private final AuditEventRepository repository;
    private final SqlReferenceValidator referenceValidator;

    public AuditEventService(AuditEventRepository repository,
            SqlReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    public AuditEvent append(Long actorId, String action, String targetType, Long targetId,
            Document beforeData, Document afterData, String reason) {
        if (actorId != null) {
            referenceValidator.requireUser(actorId);
        }
        return repository.save(new AuditEvent(null, null, actorId, action, targetType,
                targetId, beforeData, afterData, reason, Instant.now()));
    }
}
