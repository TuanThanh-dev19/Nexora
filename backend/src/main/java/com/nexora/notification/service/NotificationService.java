package com.nexora.notification.service;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.common.pagination.PaginationDefaults;
import com.nexora.common.persistence.SqlReferenceValidator;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.domain.NotificationType;
import com.nexora.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final SqlReferenceValidator referenceValidator;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public NotificationService(NotificationRepository repository,
            SqlReferenceValidator referenceValidator,
            @Autowired(required = false) MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
        this.mongoTemplate = mongoTemplate;
    }

    public NotificationService(NotificationRepository repository,
            SqlReferenceValidator referenceValidator) {
        this(repository, referenceValidator, null);
    }

    public NotificationDocument create(Long recipientId, Long ticketId, String eventKey,
            NotificationType type, String title, String message) {
        referenceValidator.requireUser(recipientId);
        if (ticketId != null) {
            referenceValidator.requireTicket(ticketId);
        }

        NotificationDocument document = new NotificationDocument(
                null, null, recipientId, ticketId, eventKey, type, title, message, false, null, Instant.now());

        try {
            return repository.save(document);
        } catch (DuplicateKeyException exception) {
            log.info("Duplicate notification prevented for eventKey={}, ticketId={}, recipientId={}",
                    eventKey, ticketId, recipientId);
            if (eventKey != null) {
                Optional<NotificationDocument> existing = repository.findByEventKey(eventKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            return document;
        }
    }

    public NotificationDocument create(Long recipientId, Long ticketId, String type,
            String title, String message) {
        NotificationType notificationType = parseType(type);
        return create(recipientId, ticketId, null, notificationType, title, message);
    }

    public NotificationDocument markRead(String notificationId, Long recipientId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new ResourceNotFoundException("Notification id is required");
        }

        if (mongoTemplate != null) {
            Query unreadQuery = Query.query(
                    Criteria.where("_id").is(notificationId)
                            .and("recipientId").is(recipientId)
                            .and("readAt").is(null));

            Update update = new Update()
                    .set("readAt", Instant.now())
                    .set("read", true);

            NotificationDocument updated = mongoTemplate.findAndModify(
                    unreadQuery,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    NotificationDocument.class);

            if (updated != null) {
                return updated;
            }

            // If not updated, verify whether it exists and is already read by this recipient
            Query existingQuery = Query.query(
                    Criteria.where("_id").is(notificationId)
                            .and("recipientId").is(recipientId));
            NotificationDocument existing = mongoTemplate.findOne(existingQuery, NotificationDocument.class);
            if (existing != null) {
                // Preserves original readAt, idempotent
                return existing;
            }

            // Either does not exist or belongs to another recipient: safe 404
            throw new ResourceNotFoundException("Notification " + notificationId + " does not exist");
        } else {
            // Fallback for tests without MongoTemplate
            NotificationDocument doc = repository.findByIdAndRecipientId(notificationId, recipientId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Notification " + notificationId + " does not exist"));
            if (doc.getReadAt() == null) {
                doc.markRead(Instant.now());
                doc = repository.save(doc);
            }
            return doc;
        }
    }

    public NotificationDocument markRead(String notificationId) {
        NotificationDocument notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification " + notificationId + " does not exist"));
        notification.markRead(Instant.now());
        return repository.save(notification);
    }

    public PageResponse<NotificationDocument> getNotifications(Long recipientId, Integer page, Integer size, boolean unreadOnly) {
        int resolvedPage = page == null ? PaginationDefaults.DEFAULT_PAGE : page;
        int resolvedSize = size == null ? PaginationDefaults.DEFAULT_PAGE_SIZE : size;

        if (resolvedPage < 0) {
            throw new InvalidPaginationException("Page must be zero or greater.");
        }
        if (resolvedSize < 1 || resolvedSize > PaginationDefaults.MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "Size must be between 1 and " + PaginationDefaults.MAX_PAGE_SIZE + ".");
        }

        Sort stableSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, stableSort);

        Page<NotificationDocument> resultPage = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNull(recipientId, pageable)
                : repository.findByRecipientId(recipientId, pageable);

        return PageResponse.from(resultPage);
    }

    public long getUnreadCount(Long recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    private static NotificationType parseType(String typeStr) {
        if (typeStr == null) return null;
        try {
            return NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
