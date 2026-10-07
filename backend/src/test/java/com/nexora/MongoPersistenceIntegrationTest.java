package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nexora.audit.document.AuditEvent;
import com.nexora.audit.repository.AuditEventRepository;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.notification.repository.NotificationRepository;
import com.nexora.ticket.document.TicketCommentDocument;
import com.nexora.ticket.repository.TicketCommentRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_MONGODB_IT", matches = "(?i)true")
class MongoPersistenceIntegrationTest {

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(
            DockerImageName.parse("mongo:8.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGODB.getReplicaSetUrl("nexora_test"));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void clearCollections() {
        auditEventRepository.deleteAll();
        ticketCommentRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void createsCollectionsIndexesAndSupportsRequiredOperations() {
        Instant now = Instant.now();
        auditEventRepository.save(new AuditEvent(null, 101L, 1L, "TICKET_UPDATED",
                "TICKET", 10L, new Document("status", "SUBMITTED"),
                new Document("status", "ASSIGNED"), null, now));
        ticketCommentRepository.save(new TicketCommentDocument(null, 201L, 10L, 1L,
                "Technician is on the way", "PUBLIC", now, null));
        NotificationDocument notification = notificationRepository.save(new NotificationDocument(
                null, 301L, 1L, 10L, "TICKET_ASSIGNED", "Ticket assigned",
                "A technician was assigned", false, null, now));

        assertThat(mongoTemplate.collectionExists("audit_events")).isTrue();
        assertThat(mongoTemplate.collectionExists("ticket_comments")).isTrue();
        assertThat(mongoTemplate.collectionExists("notifications")).isTrue();
        assertThat(auditEventRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("TICKET", 10L)).hasSize(1);
        assertThat(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(10L)).hasSize(1);
        assertThat(notificationRepository
                .findByRecipientIdAndReadOrderByCreatedAtDesc(1L, false)).hasSize(1);

        notification.markRead(now.plusSeconds(10));
        notificationRepository.save(notification);
        assertThat(notificationRepository
                .findByRecipientIdAndReadOrderByCreatedAtDesc(1L, true)).hasSize(1);

        assertThat(indexNames(AuditEvent.class)).contains("idx_audit_target_time");
        assertThat(indexNames(TicketCommentDocument.class)).contains("idx_comment_ticket_time");
        assertThat(indexNames(NotificationDocument.class))
                .contains("idx_notification_recipient_read_time");
    }

    @Test
    void legacySqlIdPreventsDuplicateBackfillDocuments() {
        ticketCommentRepository.save(new TicketCommentDocument(null, 42L, 10L, 1L,
                "first", "PUBLIC", Instant.now(), null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ticketCommentRepository.save(new TicketCommentDocument(null, 42L, 10L, 1L,
                        "duplicate", "PUBLIC", Instant.now(), null)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private java.util.List<String> indexNames(Class<?> documentType) {
        return mongoTemplate.indexOps(documentType).getIndexInfo().stream()
                .map(index -> index.getName())
                .toList();
    }
}
