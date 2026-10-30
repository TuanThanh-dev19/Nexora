package com.nexora.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.common.persistence.SqlReferenceValidator;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.domain.NotificationType;
import com.nexora.notification.repository.NotificationRepository;
import com.nexora.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationDomainAndServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private SqlReferenceValidator referenceValidator;

    @Mock
    private MongoTemplate mongoTemplate;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, referenceValidator, mongoTemplate);
    }

    @Nested
    @DisplayName("NotificationDocument Tests")
    class DocumentTests {

        @Test
        void documentStoresAllRequiredFields() {
            Instant now = Instant.now();
            NotificationDocument doc = new NotificationDocument(
                    "notif-1",
                    null,
                    100L,
                    200L,
                    "TICKET_ASSIGNED:200:1",
                    NotificationType.TICKET_ASSIGNED,
                    "Ticket Assigned",
                    "You have been assigned to ticket #T-100",
                    false,
                    null,
                    now);

            assertThat(doc.getId()).isEqualTo("notif-1");
            assertThat(doc.getRecipientId()).isEqualTo(100L);
            assertThat(doc.getTicketId()).isEqualTo(200L);
            assertThat(doc.getEventKey()).isEqualTo("TICKET_ASSIGNED:200:1");
            assertThat(doc.getType()).isEqualTo(NotificationType.TICKET_ASSIGNED);
            assertThat(doc.getTitle()).isEqualTo("Ticket Assigned");
            assertThat(doc.getMessage()).isEqualTo("You have been assigned to ticket #T-100");
            assertThat(doc.isRead()).isFalse();
            assertThat(doc.getReadAt()).isNull();
            assertThat(doc.getCreatedAt()).isEqualTo(now);
        }

        @Test
        void markReadSetsReadAndReadAtOnce() {
            NotificationDocument doc = new NotificationDocument(
                    "notif-1", null, 100L, 200L, "evt-1", NotificationType.TICKET_ASSIGNED,
                    "Title", "Msg", false, null, Instant.now());

            Instant readTime1 = Instant.parse("2026-10-30T10:00:00Z");
            doc.markRead(readTime1);

            assertThat(doc.isRead()).isTrue();
            assertThat(doc.getReadAt()).isEqualTo(readTime1);

            // Second markRead must not overwrite original readAt
            Instant readTime2 = Instant.parse("2026-10-30T12:00:00Z");
            doc.markRead(readTime2);

            assertThat(doc.getReadAt()).isEqualTo(readTime1);
        }

        @Test
        void supportsBackwardCompatibilityConstructor() {
            Instant now = Instant.now();
            NotificationDocument doc = new NotificationDocument(
                    "notif-old", 500L, 100L, 200L, "TICKET_RESOLVED", "Resolved", "Ticket resolved",
                    false, null, now);

            assertThat(doc.getId()).isEqualTo("notif-old");
            assertThat(doc.getLegacySqlId()).isEqualTo(500L);
            assertThat(doc.getType()).isEqualTo(NotificationType.TICKET_RESOLVED);
            assertThat(doc.getNotificationType()).isEqualTo("TICKET_RESOLVED");
        }
    }

    @Nested
    @DisplayName("NotificationService Create Tests")
    class CreateTests {

        @Test
        void createsNotificationWithReferenceValidation() {
            doNothing().when(referenceValidator).requireUser(100L);
            doNothing().when(referenceValidator).requireTicket(200L);

            ArgumentCaptor<NotificationDocument> captor = ArgumentCaptor.forClass(NotificationDocument.class);
            when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            NotificationDocument result = service.create(
                    100L, 200L, "TICKET_ASSIGNED:200:1", NotificationType.TICKET_ASSIGNED,
                    "New Assignment", "Assigned to ticket");

            verify(referenceValidator).requireUser(100L);
            verify(referenceValidator).requireTicket(200L);
            assertThat(result.getRecipientId()).isEqualTo(100L);
            assertThat(result.getTicketId()).isEqualTo(200L);
            assertThat(result.getEventKey()).isEqualTo("TICKET_ASSIGNED:200:1");
            assertThat(result.getType()).isEqualTo(NotificationType.TICKET_ASSIGNED);
            assertThat(result.isRead()).isFalse();
            assertThat(result.getReadAt()).isNull();
        }

        @Test
        void handlesDuplicateKeyExceptionIdempotently() {
            doNothing().when(referenceValidator).requireUser(100L);
            doNothing().when(referenceValidator).requireTicket(200L);

            NotificationDocument existing = new NotificationDocument(
                    "existing-id", null, 100L, 200L, "TICKET_ASSIGNED:200:1",
                    NotificationType.TICKET_ASSIGNED, "Title", "Msg", false, null, Instant.now());

            when(repository.save(any(NotificationDocument.class)))
                    .thenThrow(new DuplicateKeyException("Duplicate key error"));
            when(repository.findByEventKey("TICKET_ASSIGNED:200:1"))
                    .thenReturn(Optional.of(existing));

            NotificationDocument result = service.create(
                    100L, 200L, "TICKET_ASSIGNED:200:1", NotificationType.TICKET_ASSIGNED,
                    "Title", "Msg");

            assertThat(result).isSameAs(existing);
        }

        @Test
        void failsWhenUserOrTicketNotFound() {
            doThrow(new ResourceNotFoundException("User 999 does not exist"))
                    .when(referenceValidator).requireUser(999L);

            assertThatThrownBy(() -> service.create(999L, 200L, "key", NotificationType.TICKET_ASSIGNED, "T", "M"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User 999 does not exist");
        }
    }

    @Nested
    @DisplayName("NotificationService Mark Read Tests")
    class MarkReadTests {

        @Test
        void markReadUpdatesAtomicallyWhenUnread() {
            Instant readTime = Instant.now();
            NotificationDocument updatedDoc = new NotificationDocument(
                    "notif-1", null, 100L, 200L, "key-1", NotificationType.TICKET_ASSIGNED,
                    "Title", "Msg", true, readTime, Instant.now());

            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(NotificationDocument.class)))
                    .thenReturn(updatedDoc);

            NotificationDocument result = service.markRead("notif-1", 100L);

            assertThat(result.isRead()).isTrue();
            assertThat(result.getReadAt()).isEqualTo(readTime);
        }

        @Test
        void markReadIsIdempotentAndPreservesInitialReadAtWhenAlreadyRead() {
            Instant initialReadAt = Instant.parse("2026-10-30T09:00:00Z");
            NotificationDocument alreadyReadDoc = new NotificationDocument(
                    "notif-1", null, 100L, 200L, "key-1", NotificationType.TICKET_ASSIGNED,
                    "Title", "Msg", true, initialReadAt, Instant.now());

            // findAndModify returns null because readAt is NOT null
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(NotificationDocument.class)))
                    .thenReturn(null);
            // findOne returns existing document for this recipient
            when(mongoTemplate.findOne(any(Query.class), eq(NotificationDocument.class)))
                    .thenReturn(alreadyReadDoc);

            NotificationDocument result = service.markRead("notif-1", 100L);

            assertThat(result.isRead()).isTrue();
            assertThat(result.getReadAt()).isEqualTo(initialReadAt);
        }

        @Test
        void markReadThrowsNotFoundWhenDocMissingOrBelongsToAnotherRecipient() {
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(NotificationDocument.class)))
                    .thenReturn(null);
            when(mongoTemplate.findOne(any(Query.class), eq(NotificationDocument.class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.markRead("notif-999", 100L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Notification notif-999 does not exist");
        }
    }

    @Nested
    @DisplayName("NotificationService Pagination & Query Tests")
    class PaginationAndQueryTests {

        @Test
        void getNotificationsValidatesPaginationParameters() {
            assertThatThrownBy(() -> service.getNotifications(100L, -1, 20, false))
                    .isInstanceOf(InvalidPaginationException.class)
                    .hasMessageContaining("Page must be zero or greater.");

            assertThatThrownBy(() -> service.getNotifications(100L, 0, 0, false))
                    .isInstanceOf(InvalidPaginationException.class)
                    .hasMessageContaining("Size must be between 1 and 100.");

            assertThatThrownBy(() -> service.getNotifications(100L, 0, 101, false))
                    .isInstanceOf(InvalidPaginationException.class)
                    .hasMessageContaining("Size must be between 1 and 100.");
        }

        @Test
        void getNotificationsQueriesAllOrUnreadOnly() {
            NotificationDocument doc1 = new NotificationDocument(
                    "notif-1", null, 100L, 200L, "key-1", NotificationType.TICKET_ASSIGNED,
                    "T1", "M1", false, null, Instant.now());
            Page<NotificationDocument> page = new PageImpl<>(List.of(doc1));

            when(repository.findByRecipientId(eq(100L), any(Pageable.class))).thenReturn(page);
            PageResponse<NotificationDocument> allResponse = service.getNotifications(100L, 0, 20, false);
            assertThat(allResponse.content()).hasSize(1);
            verify(repository).findByRecipientId(eq(100L), any(Pageable.class));

            when(repository.findByRecipientIdAndReadAtIsNull(eq(100L), any(Pageable.class))).thenReturn(page);
            PageResponse<NotificationDocument> unreadResponse = service.getNotifications(100L, 0, 20, true);
            assertThat(unreadResponse.content()).hasSize(1);
            verify(repository).findByRecipientIdAndReadAtIsNull(eq(100L), any(Pageable.class));
        }

        @Test
        void getUnreadCountReturnsCountFromRepository() {
            when(repository.countByRecipientIdAndReadAtIsNull(100L)).thenReturn(5L);
            long count = service.getUnreadCount(100L);
            assertThat(count).isEqualTo(5L);
            verify(repository).countByRecipientIdAndReadAtIsNull(100L);
        }
    }
}

