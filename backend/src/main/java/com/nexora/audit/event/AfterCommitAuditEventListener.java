package com.nexora.audit.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexora.audit.service.AuditEventService;

@Component
public class AfterCommitAuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitAuditEventListener.class);

    private final AuditEventService auditEventService;

    public AfterCommitAuditEventListener(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void persistAfterSqlCommit(AuditEventRequested event) {
        try {
            auditEventService.append(event.actorId(), event.action(), event.targetType(),
                    event.targetId(), event.beforeData(), event.afterData(), event.reason());
        } catch (RuntimeException exception) {
            log.error("SQL transaction committed, but its audit event could not be written to MongoDB: "
                    + "action={}, targetType={}, targetId={}",
                    event.action(), event.targetType(), event.targetId(), exception);
        }
    }
}
