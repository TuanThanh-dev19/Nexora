package com.nexora.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.nexora.notification.document.NotificationDocument;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    Optional<NotificationDocument> findByLegacySqlId(Long legacySqlId);

    Optional<NotificationDocument> findByEventKey(String eventKey);

    Optional<NotificationDocument> findByIdAndRecipientId(String id, Long recipientId);

    List<NotificationDocument> findByRecipientIdAndReadOrderByCreatedAtDesc(Long recipientId, boolean read);

    Page<NotificationDocument> findByRecipientId(Long recipientId, Pageable pageable);

    Page<NotificationDocument> findByRecipientIdAndReadAtIsNull(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
