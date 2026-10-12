package com.nexora.audit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.nexora.audit.document.AuditEvent;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    Optional<AuditEvent> findByLegacySqlId(Long legacySqlId);

    List<AuditEvent> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
