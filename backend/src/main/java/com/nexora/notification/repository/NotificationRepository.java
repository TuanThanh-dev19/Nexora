package com.nexora.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.nexora.notification.document.NotificationDocument;

public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {

    Optional<NotificationDocument> findByLegacySqlId(Long legacySqlId);

    List<NotificationDocument> findByRecipientIdAndReadOrderByCreatedAtDesc(Long recipientId, boolean read);
}
