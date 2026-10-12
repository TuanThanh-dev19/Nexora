package com.nexora.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.audit.document.AuditEvent;
import com.nexora.notification.document.NotificationDocument;
import com.nexora.ticket.document.TicketCommentDocument;

@Component
@ConditionalOnProperty(name = "app.mongodb.backfill.enabled", havingValue = "true")
@Order(100)
public class LegacyDocumentBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDocumentBackfillRunner.class);
    private static final String VERIFICATION_PROPERTY = "NexoraMongoBackfillVerified";

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public LegacyDocumentBackfillRunner(JdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        requireLegacyTables();

        List<AuditRow> auditRows = jdbcTemplate.query("""
                SELECT id, actor_id, action, target_type, target_id,
                       before_data, after_data, reason, created_at
                FROM dbo.audit_logs
                ORDER BY id
                """, this::mapAuditRow);
        List<CommentRow> commentRows = jdbcTemplate.query("""
                SELECT id, ticket_id, author_id, body, visibility, created_at, edited_at
                FROM dbo.ticket_comments
                ORDER BY id
                """, this::mapCommentRow);
        List<NotificationRow> notificationRows = jdbcTemplate.query("""
                SELECT id, recipient_id, ticket_id, notification_type,
                       title, message, read_at, created_at
                FROM dbo.notifications
                ORDER BY id
                """, this::mapNotificationRow);

        log.info("Legacy SQL counts before MongoDB backfill: audit_logs={}, ticket_comments={}, notifications={}",
                auditRows.size(), commentRows.size(), notificationRows.size());

        auditRows.forEach(this::upsertAuditEvent);
        commentRows.forEach(this::upsertComment);
        notificationRows.forEach(this::upsertNotification);

        long migratedAudits = countMigrated(AuditEvent.class);
        long migratedComments = countMigrated(TicketCommentDocument.class);
        long migratedNotifications = countMigrated(NotificationDocument.class);

        verifyCount("audit_logs", auditRows.size(), migratedAudits);
        verifyCount("ticket_comments", commentRows.size(), migratedComments);
        verifyCount("notifications", notificationRows.size(), migratedNotifications);

        String verification = verificationJson(
                migratedAudits, migratedComments, migratedNotifications);
        writeVerificationProperty(verification);

        log.info("MongoDB backfill verified: audit_events={}, ticket_comments={}, notifications={}. "
                        + "Restart without MONGO_BACKFILL_ENABLED and without spring.flyway.target to apply V8.",
                migratedAudits, migratedComments, migratedNotifications);
    }

    private void requireLegacyTables() {
        List<String> missing = List.of("audit_logs", "ticket_comments", "notifications").stream()
                .filter(table -> Boolean.FALSE.equals(jdbcTemplate.queryForObject(
                        "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END",
                        Boolean.class, "dbo." + table)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Legacy SQL tables are unavailable: " + missing
                    + ". Backfill must run with spring.flyway.target=7 before V8 is applied.");
        }
    }

    private AuditRow mapAuditRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AuditRow(
                resultSet.getLong("id"),
                nullableLong(resultSet, "actor_id"),
                resultSet.getString("action"),
                resultSet.getString("target_type"),
                resultSet.getLong("target_id"),
                parseJson(resultSet.getString("before_data")),
                parseJson(resultSet.getString("after_data")),
                resultSet.getString("reason"),
                instant(resultSet, "created_at"));
    }

    private CommentRow mapCommentRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CommentRow(
                resultSet.getLong("id"),
                resultSet.getLong("ticket_id"),
                resultSet.getLong("author_id"),
                resultSet.getString("body"),
                resultSet.getString("visibility"),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "edited_at"));
    }

    private NotificationRow mapNotificationRow(ResultSet resultSet, int rowNumber) throws SQLException {
        Instant readAt = nullableInstant(resultSet, "read_at");
        return new NotificationRow(
                resultSet.getLong("id"),
                resultSet.getLong("recipient_id"),
                nullableLong(resultSet, "ticket_id"),
                resultSet.getString("notification_type"),
                resultSet.getString("title"),
                resultSet.getString("message"),
                readAt,
                instant(resultSet, "created_at"));
    }

    private void upsertAuditEvent(AuditRow row) {
        Update update = new Update()
                .set("legacySqlId", row.id())
                .set("actorId", row.actorId())
                .set("action", row.action())
                .set("targetType", row.targetType())
                .set("targetId", row.targetId())
                .set("before", row.beforeData())
                .set("after", row.afterData())
                .set("reason", row.reason())
                .set("createdAt", row.createdAt());
        mongoTemplate.upsert(byLegacyId(row.id()), update, AuditEvent.class);
    }

    private void upsertComment(CommentRow row) {
        Update update = new Update()
                .set("legacySqlId", row.id())
                .set("ticketId", row.ticketId())
                .set("authorId", row.authorId())
                .set("body", row.body())
                .set("visibility", row.visibility())
                .set("createdAt", row.createdAt())
                .set("editedAt", row.editedAt());
        mongoTemplate.upsert(byLegacyId(row.id()), update, TicketCommentDocument.class);
    }

    private void upsertNotification(NotificationRow row) {
        Update update = new Update()
                .set("legacySqlId", row.id())
                .set("recipientId", row.recipientId())
                .set("ticketId", row.ticketId())
                .set("notificationType", row.notificationType())
                .set("title", row.title())
                .set("message", row.message())
                .set("read", row.readAt() != null)
                .set("readAt", row.readAt())
                .set("createdAt", row.createdAt());
        mongoTemplate.upsert(byLegacyId(row.id()), update, NotificationDocument.class);
    }

    private Query byLegacyId(long id) {
        return Query.query(Criteria.where("legacySqlId").is(id));
    }

    private long countMigrated(Class<?> documentType) {
        return mongoTemplate.count(
                Query.query(Criteria.where("legacySqlId").exists(true)), documentType);
    }

    private void verifyCount(String source, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException("MongoDB backfill verification failed for " + source
                    + ": SQL=" + expected + ", MongoDB=" + actual);
        }
    }

    private String verificationJson(long audits, long comments, long notifications)
            throws JsonProcessingException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("auditLogs", audits);
        values.put("ticketComments", comments);
        values.put("notifications", notifications);
        values.put("verifiedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(values);
    }

    private void writeVerificationProperty(String verification) {
        jdbcTemplate.update("""
                DECLARE @property_value NVARCHAR(4000) = ?;
                IF EXISTS (
                    SELECT 1 FROM sys.extended_properties
                    WHERE class = 0 AND name = N'NexoraMongoBackfillVerified'
                )
                    EXEC sys.sp_updateextendedproperty
                        @name = N'NexoraMongoBackfillVerified', @value = @property_value;
                ELSE
                    EXEC sys.sp_addextendedproperty
                        @name = N'NexoraMongoBackfillVerified', @value = @property_value;
                """, verification);
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Document parseJson(String json) {
        return json == null ? null : Document.parse(json);
    }

    private record AuditRow(long id, Long actorId, String action, String targetType,
            long targetId, Document beforeData, Document afterData, String reason, Instant createdAt) {
    }

    private record CommentRow(long id, long ticketId, long authorId, String body,
            String visibility, Instant createdAt, Instant editedAt) {
    }

    private record NotificationRow(long id, long recipientId, Long ticketId,
            String notificationType, String title, String message, Instant readAt, Instant createdAt) {
    }
}
