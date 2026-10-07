package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.entity.FieldDefinition;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAttachment;
import com.nexora.ticket.entity.TicketFieldValue;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.repository.TicketAttachmentRepository;
import com.nexora.ticket.repository.TicketFieldValueRepository;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.AppUser;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TicketSupportingPersistenceSqlServerIntegrationTest {

    // DATETIMEOFFSET(7) preserves 100 ns precision and the original offset.
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-06T08:30:00.1234567+07:00");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TicketFieldValueRepository values;
    @Autowired
    private TicketStatusHistoryRepository history;
    @Autowired
    private TicketAttachmentRepository attachments;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("jsonValues")
    void roundTripsCanonicalJsonAgainstIsJsonValueConstraint(
            DynamicFieldType type, String raw, JsonNodeType nodeType) throws Exception {
        Fixture f = fixture("JSON");
        FieldDefinition field = persist(new FieldDefinition(f.form(), "answer", "Câu trả lời", type, 0));
        TicketFieldValue saved = values.saveAndFlush(new TicketFieldValue(f.ticket(), field, raw));
        entityManager.clear();

        TicketFieldValue loaded = values.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getValueJson()).isEqualTo(raw);
        assertThat(json.readTree(loaded.getValueJson()).getNodeType()).isEqualTo(nodeType);
        assertThat(loaded.getTicket().getId()).isEqualTo(f.ticket().getId());
        assertThat(loaded.getFieldDefinition().getId()).isEqualTo(field.getId());
        assertThat(loaded.getFormVersionId()).isEqualTo(f.form().getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    static Stream<Arguments> jsonValues() {
        return Stream.of(
                Arguments.of(DynamicFieldType.TEXT, "\"Máy chiếu \\\"A\\\"\\nkhông hoạt động\"", JsonNodeType.STRING),
                Arguments.of(DynamicFieldType.TEXTAREA, "\"" + "Mô tả dài ".repeat(700) + "\"", JsonNodeType.STRING),
                Arguments.of(DynamicFieldType.SELECT, "\"projector\"", JsonNodeType.STRING),
                Arguments.of(DynamicFieldType.NUMBER, "12.50", JsonNodeType.NUMBER),
                Arguments.of(DynamicFieldType.NUMBER, "9007199254740993", JsonNodeType.NUMBER),
                Arguments.of(DynamicFieldType.NUMBER, "1e3", JsonNodeType.NUMBER),
                Arguments.of(DynamicFieldType.BOOLEAN, "false", JsonNodeType.BOOLEAN),
                Arguments.of(DynamicFieldType.MULTI_SELECT, "[ \"projector\", \"screen\" ]", JsonNodeType.ARRAY),
                Arguments.of(DynamicFieldType.DATE, "\"2026-10-06\"", JsonNodeType.STRING),
                Arguments.of(DynamicFieldType.DATETIME, "\"2026-10-06T08:30:00+07:00\"", JsonNodeType.STRING));
    }

    @Test
    void ordersValuesByFieldOrderThenFieldIdOnSqlServer() {
        Fixture f = fixture("ORDER");
        FieldDefinition later = persist(new FieldDefinition(f.form(), "later", "Later", DynamicFieldType.TEXT, 9));
        FieldDefinition first = persist(new FieldDefinition(f.form(), "first", "First", DynamicFieldType.TEXT, 0));
        FieldDefinition tied = persist(new FieldDefinition(f.form(), "tied", "Tied", DynamicFieldType.TEXT, 0));
        values.saveAndFlush(new TicketFieldValue(f.ticket(), tied, "\"tied\""));
        values.saveAndFlush(new TicketFieldValue(f.ticket(), later, "\"later\""));
        values.saveAndFlush(new TicketFieldValue(f.ticket(), first, "\"first\""));
        values.saveAndFlush(new TicketFieldValue(otherTicket(f, "OTHER-ORDER"), first, "\"other\""));
        entityManager.clear();

        assertThat(values.findByTicketIdOrderByFieldOrder(f.ticket().getId()))
                .extracting(v -> v.getFieldDefinition().getId())
                .containsExactly(first.getId(), tied.getId(), later.getId());
    }

    @Test
    void roundTripsAndOrdersInitialAndSameStatusHistoryWithOffsetAndPrecision() {
        Fixture f = fixture("HISTORY");
        TicketStatusHistory newer = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW, TicketAction.BEGIN_REVIEW,
                "Đã nhận báo cáo", f.actor(), TIME.plusDays(1)));
        TicketStatusHistory initial = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                null, TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor(), TIME));
        TicketStatusHistory tied = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                TicketStatus.ASSIGNED, TicketStatus.ASSIGNED, TicketAction.REASSIGN,
                "Đổi kỹ thuật viên", f.actor(), TIME));
        history.saveAndFlush(new TicketStatusHistory(otherTicket(f, "OTHER-HISTORY"), null,
                TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor(), TIME));
        entityManager.clear();

        var loaded = history.findByTicketIdOrderByChangedAtAscIdAsc(f.ticket().getId());
        assertThat(loaded).extracting(TicketStatusHistory::getId)
                .containsExactly(initial.getId(), tied.getId(), newer.getId());
        assertThat(loaded.getFirst().getFromStatus()).isNull();
        assertThat(loaded.getFirst().getReason()).isNull();
        assertThat(loaded.getFirst().getChangedAt()).isEqualTo(TIME);
        assertThat(loaded.getFirst().getToStatus()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(loaded.getFirst().getTransitionAction()).isEqualTo(TicketAction.SUBMIT);
        assertThat(loaded.get(1).getFromStatus()).isEqualTo(loaded.get(1).getToStatus());
        assertThat(loaded.get(1).getTransitionAction()).isEqualTo(TicketAction.REASSIGN);
        assertThat(loaded.get(1).getReason()).isEqualTo("Đổi kỹ thuật viên");
        assertThat(loaded.getLast().getChangedBy().getId()).isEqualTo(f.actor().getId());
    }

    @Test
    void roundTripsAndFiltersMetadataIncludingImageWithoutAValueRow() {
        Fixture f = fixture("ATTACH");
        TicketAttachment evidence = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.REPORT_EVIDENCE, "sql-evidence", TIME.plusDays(1)));
        TicketAttachment image = attachments.saveAndFlush(attachment(f, f.image(),
                AttachmentType.REPORT_EVIDENCE, "sql-field-image", TIME));
        TicketAttachment work = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.WORK_RESULT, "sql-work", TIME));
        TicketAttachment comment = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.COMMENT, "sql-reserved-comment", TIME.plusDays(2)));
        attachments.saveAndFlush(new TicketAttachment(otherTicket(f, "OTHER-ATTACH"), null, f.actor(),
                AttachmentType.REPORT_EVIDENCE, "sql-other", "other.png", "image/png", 1, TIME));
        entityManager.clear();

        assertThat(attachments.findByTicketIdOrderByCreatedAtAscIdAsc(f.ticket().getId()))
                .extracting(TicketAttachment::getId)
                .containsExactly(image.getId(), work.getId(), evidence.getId(), comment.getId());
        var reports = attachments.findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
                f.ticket().getId(), AttachmentType.REPORT_EVIDENCE);
        assertThat(reports).extracting(TicketAttachment::getId).containsExactly(image.getId(), evidence.getId());
        TicketAttachment loaded = reports.getFirst();
        assertThat(loaded.getTicket().getId()).isEqualTo(f.ticket().getId());
        assertThat(loaded.getFormVersionId()).isEqualTo(f.form().getId());
        assertThat(loaded.getFieldDefinition().getId()).isEqualTo(f.image().getId());
        assertThat(loaded.getUploadedBy().getId()).isEqualTo(f.actor().getId());
        assertThat(loaded.getStorageKey()).isEqualTo("sql-field-image");
        assertThat(loaded.getOriginalName()).isEqualTo("../ảnh.png");
        assertThat(loaded.getContentType()).isEqualTo("image/png");
        assertThat(loaded.getSizeBytes()).isEqualTo(2_147_483_648L);
        assertThat(loaded.getCreatedAt()).isEqualTo(TIME);
        assertThat(values.findByTicketIdOrderByFieldOrder(f.ticket().getId())).isEmpty();
        assertThat(attachments.findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
                f.ticket().getId(), AttachmentType.WORK_RESULT)).singleElement().satisfies(a -> {
                    assertThat(a.getFieldDefinition()).isNull();
                    assertThat(a.getFormVersionId()).isEqualTo(f.form().getId());
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-json", "duplicate-value", "value-ticket-form", "value-field-form",
            "value-missing-ticket", "value-missing-field", "attachment-ticket-form", "attachment-field-form",
            "attachment-missing-ticket", "attachment-missing-field", "attachment-missing-user",
            "duplicate-storage-key", "zero-size", "negative-size", "invalid-attachment-type",
            "history-missing-ticket", "history-missing-user", "history-invalid-from", "history-invalid-to"})
    void enforcesNamedConstraintsAtTheSqlServerBoundary(String violation) {
        Fixture f = fixture("CONSTRAINT");
        CategoryFormVersion otherForm = persist(new CategoryFormVersion(f.category(), 2));
        FieldDefinition otherField = persist(new FieldDefinition(otherForm,
                "other", "Other field", DynamicFieldType.IMAGE, 0));
        long ticketId = f.ticket().getId();
        long formId = f.form().getId();
        long fieldId = f.answer().getId();
        long actorId = f.actor().getId();
        String key = "sql-constraint-key";

        if (violation.equals("duplicate-value")) {
            insertValue(ticketId, formId, fieldId, "\"first\"");
        }
        if (violation.equals("duplicate-storage-key")) {
            insertAttachment(ticketId, formId, null, actorId, "REPORT_EVIDENCE", key, 1);
        }

        String constraint = switch (violation) {
            case "invalid-json" -> "ck_ticket_field_values_json";
            case "duplicate-value" -> "uq_ticket_field_values_field";
            case "value-ticket-form", "value-missing-ticket" -> "fk_ticket_field_values_ticket_form";
            case "value-field-form", "value-missing-field" -> "fk_ticket_field_values_definition_form";
            case "attachment-ticket-form" -> "fk_ticket_attachments_ticket_form";
            case "attachment-field-form" -> "fk_ticket_attachments_definition_form";
            // Missing IDs violate both the original FK and its composite extension.
            case "attachment-missing-ticket" -> "fk_ticket_attachments_ticket";
            case "attachment-missing-field" -> "fk_ticket_attachments_";
            case "attachment-missing-user" -> "fk_ticket_attachments_user";
            case "duplicate-storage-key" -> "uq_ticket_attachments_storage_key";
            case "zero-size", "negative-size" -> "ck_ticket_attachments_size";
            case "invalid-attachment-type" -> "ck_ticket_attachments_type";
            case "history-missing-ticket" -> "fk_ticket_status_history_ticket";
            case "history-missing-user" -> "fk_ticket_status_history_user";
            case "history-invalid-from" -> "ck_ticket_status_history_from";
            case "history-invalid-to" -> "ck_ticket_status_history_to";
            default -> throw new IllegalArgumentException(violation);
        };

        assertThatThrownBy(() -> {
            switch (violation) {
                case "invalid-json" -> insertValue(ticketId, formId, fieldId, "not JSON");
                case "duplicate-value" -> insertValue(ticketId, formId, fieldId, "\"second\"");
                case "value-ticket-form" -> insertValue(ticketId, otherForm.getId(), otherField.getId(), "false");
                case "value-field-form" -> insertValue(ticketId, formId, otherField.getId(), "false");
                case "value-missing-ticket" -> insertValue(-1L, formId, fieldId, "false");
                case "value-missing-field" -> insertValue(ticketId, formId, -1L, "false");
                case "attachment-ticket-form" -> insertAttachment(ticketId, otherForm.getId(), otherField.getId(),
                        actorId, "REPORT_EVIDENCE", key, 1);
                case "attachment-field-form" -> insertAttachment(ticketId, formId, otherField.getId(),
                        actorId, "REPORT_EVIDENCE", key, 1);
                case "attachment-missing-ticket" -> insertAttachment(-1L, formId, null,
                        actorId, "REPORT_EVIDENCE", key, 1);
                case "attachment-missing-field" -> insertAttachment(ticketId, formId, -1L,
                        actorId, "REPORT_EVIDENCE", key, 1);
                case "attachment-missing-user" -> insertAttachment(ticketId, formId, null,
                        -1L, "REPORT_EVIDENCE", key, 1);
                case "duplicate-storage-key" -> insertAttachment(otherTicket(f, "DUP-KEY").getId(), formId,
                        null, actorId, "WORK_RESULT", key, 1);
                case "zero-size" -> insertAttachment(ticketId, formId, null, actorId, "REPORT_EVIDENCE", key, 0);
                case "negative-size" -> insertAttachment(ticketId, formId, null, actorId, "REPORT_EVIDENCE", key, -1);
                case "invalid-attachment-type" -> insertAttachment(ticketId, formId, null, actorId, "IMAGE", key, 1);
                case "history-missing-ticket" -> insertHistory(-1L, actorId, null, "SUBMITTED");
                case "history-missing-user" -> insertHistory(ticketId, -1L, null, "SUBMITTED");
                case "history-invalid-from" -> insertHistory(ticketId, actorId, "NEW_REQUEST", "SUBMITTED");
                case "history-invalid-to" -> insertHistory(ticketId, actorId, null, "INVALID");
                default -> throw new IllegalArgumentException(violation);
            }
        }).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"value", "history", "attachment", "field", "uploader", "history-actor"})
    void preventsDeletionOfReferencedParents(String reference) {
        Fixture f = fixture("DELETE-PARENT");
        switch (reference) {
            case "value" -> values.saveAndFlush(new TicketFieldValue(f.ticket(), f.answer(), "\"value\""));
            case "history", "history-actor" -> history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                    null, TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor()));
            default -> attachments.saveAndFlush(attachment(f, f.image(),
                    AttachmentType.REPORT_EVIDENCE, "sql-delete-parent", TIME));
        }
        entityManager.clear();

        String constraint = switch (reference) {
            case "value" -> "fk_ticket_field_values_ticket_form";
            case "history" -> "fk_ticket_status_history_ticket";
            case "attachment" -> "fk_ticket_attachments_ticket";
            case "field" -> "fk_ticket_attachments_";
            case "uploader" -> "fk_ticket_attachments_user";
            case "history-actor" -> "fk_ticket_status_history_user";
            default -> throw new IllegalArgumentException(reference);
        };
        assertThatThrownBy(() -> {
            switch (reference) {
                case "field" -> jdbc.update("DELETE FROM dbo.field_definitions WHERE id = ?", f.image().getId());
                case "uploader", "history-actor" -> jdbc.update("DELETE FROM dbo.app_users WHERE id = ?", f.actor().getId());
                default -> jdbc.update("DELETE FROM dbo.tickets WHERE id = ?", f.ticket().getId());
            }
        }).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining(constraint);
    }

    @Test
    void deletingChildMetadataDoesNotDeleteReferenceData() {
        Fixture f = fixture("DELETE-CHILD");
        TicketFieldValue value = values.saveAndFlush(new TicketFieldValue(f.ticket(), f.answer(), "\"value\""));
        TicketAttachment attachment = attachments.saveAndFlush(attachment(f, f.image(),
                AttachmentType.REPORT_EVIDENCE, "sql-delete-child", TIME));
        values.delete(value);
        attachments.delete(attachment);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Ticket.class, f.ticket().getId())).isNotNull();
        assertThat(entityManager.find(FieldDefinition.class, f.image().getId())).isNotNull();
        assertThat(entityManager.find(FieldDefinition.class, f.answer().getId())).isNotNull();
        assertThat(entityManager.find(CategoryFormVersion.class, f.form().getId())).isNotNull();
        assertThat(entityManager.find(IncidentCategory.class, f.category().getId())).isNotNull();
        assertThat(entityManager.find(AppUser.class, f.actor().getId())).isNotNull();
        assertThat(entityManager.find(AppUser.class, f.reporter().getId())).isNotNull();
        assertThat(entityManager.find(Location.class, f.location().getId())).isNotNull();
    }

    @Test
    void verifiesColumnNamesTypesConstraintsAndAbsenceOfBinaryColumns() {
        assertColumns("ticket_field_values", List.of("id:bigint", "ticket_id:bigint", "form_version_id:bigint",
                "field_definition_id:bigint", "value_json:nvarchar", "created_at:datetimeoffset"));
        assertColumns("ticket_status_history", List.of("id:bigint", "ticket_id:bigint", "from_status:varchar",
                "to_status:varchar", "transition_action:varchar", "reason:nvarchar",
                "changed_by:bigint", "changed_at:datetimeoffset"));
        assertColumns("ticket_attachments", List.of("id:bigint", "ticket_id:bigint", "form_version_id:bigint",
                "field_definition_id:bigint", "uploaded_by:bigint", "attachment_type:varchar",
                "storage_key:nvarchar", "original_name:nvarchar", "content_type:varchar",
                "size_bytes:bigint", "created_at:datetimeoffset"));
        assertColumn("ticket_field_values", "value_json", -1, false);
        assertColumn("ticket_field_values", "form_version_id", 8, false);
        assertColumn("ticket_status_history", "from_status", 30, true);
        assertColumn("ticket_status_history", "to_status", 30, false);
        assertColumn("ticket_status_history", "transition_action", 40, false);
        assertColumn("ticket_status_history", "reason", 2000, true);
        assertColumn("ticket_status_history", "changed_by", 8, false);
        assertColumn("ticket_attachments", "form_version_id", 8, false);
        assertColumn("ticket_attachments", "field_definition_id", 8, true);
        assertColumn("ticket_attachments", "uploaded_by", 8, false);
        assertColumn("ticket_attachments", "attachment_type", 30, false);
        assertColumn("ticket_attachments", "storage_key", 1000, false);
        assertColumn("ticket_attachments", "original_name", 510, false);
        assertColumn("ticket_attachments", "content_type", 120, false);
        assertColumn("ticket_attachments", "size_bytes", 8, false);
        for (String table : List.of("ticket_field_values", "ticket_status_history", "ticket_attachments")) {
            String timestamp = table.equals("ticket_status_history") ? "changed_at" : "created_at";
            assertColumn(table, timestamp, 10, false);
            assertThat(jdbc.queryForObject("""
                    SELECT scale FROM sys.columns WHERE object_id = OBJECT_ID(?) AND name = ?
                    """, Integer.class, "dbo." + table, timestamp)).isEqualTo(7);
            assertThat(jdbc.queryForObject("""
                    SELECT is_identity FROM sys.columns WHERE object_id = OBJECT_ID(?) AND name = 'id'
                    """, Boolean.class, "dbo." + table)).isTrue();
        }

        var foreignKeys = jdbc.queryForList("""
                SELECT name, is_disabled, is_not_trusted, delete_referential_action_desc
                FROM sys.foreign_keys WHERE parent_object_id IN (
                    OBJECT_ID('dbo.ticket_field_values'), OBJECT_ID('dbo.ticket_status_history'),
                    OBJECT_ID('dbo.ticket_attachments'))
                """);
        assertThat(foreignKeys).extracting(row -> row.get("name")).containsExactlyInAnyOrder(
                "fk_ticket_field_values_ticket_form", "fk_ticket_field_values_definition_form",
                "fk_ticket_status_history_ticket", "fk_ticket_status_history_user",
                "fk_ticket_attachments_ticket", "fk_ticket_attachments_field", "fk_ticket_attachments_user",
                "fk_ticket_attachments_ticket_form", "fk_ticket_attachments_definition_form");
        assertThat(foreignKeys).allSatisfy(row -> {
            assertThat(row.get("is_disabled")).isEqualTo(false);
            assertThat(row.get("is_not_trusted")).isEqualTo(false);
            assertThat(row.get("delete_referential_action_desc")).isEqualTo("NO_ACTION");
        });
        assertThat(jdbc.queryForList("""
                SELECT name FROM sys.check_constraints WHERE parent_object_id IN (
                    OBJECT_ID('dbo.ticket_field_values'), OBJECT_ID('dbo.ticket_status_history'),
                    OBJECT_ID('dbo.ticket_attachments')) AND is_disabled = 0 AND is_not_trusted = 0
                """, String.class)).containsExactlyInAnyOrder("ck_ticket_field_values_json",
                        "ck_ticket_status_history_from", "ck_ticket_status_history_to",
                        "ck_ticket_attachments_type", "ck_ticket_attachments_size");
        assertThat(jdbc.queryForList("""
                SELECT name FROM sys.key_constraints WHERE type = 'UQ' AND parent_object_id IN (
                    OBJECT_ID('dbo.ticket_field_values'), OBJECT_ID('dbo.ticket_attachments'))
                """, String.class)).containsExactlyInAnyOrder(
                        "uq_ticket_field_values_field", "uq_ticket_attachments_storage_key");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ticketUpdateAndHistoryShareCommitAndRollback() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture f = transaction.execute(status -> fixture("TRANSACTION"));
        assertThat(f).isNotNull();
        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                updateTicketAndAppendHistory(f);
                throw new IllegalStateException("force rollback");
            })).isInstanceOf(IllegalStateException.class).hasMessage("force rollback");

            assertThat(ticketStatus(f)).isEqualTo("SUBMITTED");
            assertThat(ticketVersion(f)).isZero();
            assertThat(historyCount(f)).isZero();

            transaction.executeWithoutResult(status -> updateTicketAndAppendHistory(f));

            assertThat(ticketStatus(f)).isEqualTo("UNDER_REVIEW");
            assertThat(ticketVersion(f)).isEqualTo(1L);
            assertThat(historyCount(f)).isEqualTo(1);
            transaction.executeWithoutResult(status ->
                    assertThat(history.findByTicketIdOrderByChangedAtAscIdAsc(f.ticket().getId()))
                            .singleElement().satisfies(h -> {
                                assertThat(h.getFromStatus()).isEqualTo(TicketStatus.SUBMITTED);
                                assertThat(h.getToStatus()).isEqualTo(TicketStatus.UNDER_REVIEW);
                                assertThat(h.getTransitionAction()).isEqualTo(TicketAction.BEGIN_REVIEW);
                            }));
        } finally {
            // Only this test's committed rows; never remove seeds or another module's data.
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM dbo.ticket_status_history WHERE ticket_id = ?", f.ticket().getId());
                jdbc.update("DELETE FROM dbo.tickets WHERE id = ?", f.ticket().getId());
                jdbc.update("DELETE FROM dbo.field_definitions WHERE id IN (?, ?)", f.image().getId(), f.answer().getId());
                jdbc.update("DELETE FROM dbo.category_form_versions WHERE id = ?", f.form().getId());
                jdbc.update("DELETE FROM dbo.incident_categories WHERE id = ?", f.category().getId());
                jdbc.update("DELETE FROM dbo.locations WHERE id = ?", f.location().getId());
                jdbc.update("DELETE FROM dbo.app_users WHERE id IN (?, ?)", f.reporter().getId(), f.actor().getId());
            });
        }
    }

    private void updateTicketAndAppendHistory(Fixture f) {
        jdbc.update("""
                UPDATE dbo.tickets SET ticket_status = 'UNDER_REVIEW', version = version + 1,
                    updated_at = ? WHERE id = ?
                """, TIME, f.ticket().getId());
        history.saveAndFlush(new TicketStatusHistory(entityManager.getReference(Ticket.class, f.ticket().getId()),
                TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW, TicketAction.BEGIN_REVIEW, null,
                entityManager.getReference(AppUser.class, f.actor().getId()), TIME));
    }

    private String ticketStatus(Fixture f) {
        return jdbc.queryForObject("SELECT ticket_status FROM dbo.tickets WHERE id = ?",
                String.class, f.ticket().getId());
    }

    private Long ticketVersion(Fixture f) {
        return jdbc.queryForObject("SELECT version FROM dbo.tickets WHERE id = ?", Long.class, f.ticket().getId());
    }

    private Integer historyCount(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ticket_status_history WHERE ticket_id = ?",
                Integer.class, f.ticket().getId());
    }

    private void assertColumns(String table, List<String> expected) {
        assertThat(jdbc.queryForList("""
                SELECT c.name + ':' + t.name FROM sys.columns c
                JOIN sys.types t ON c.user_type_id = t.user_type_id
                WHERE c.object_id = OBJECT_ID(?)
                """, String.class, "dbo." + table)).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertColumn(String table, String column, int maxLength, boolean nullable) {
        var definition = jdbc.queryForMap("""
                SELECT max_length, is_nullable FROM sys.columns WHERE object_id = OBJECT_ID(?) AND name = ?
                """, "dbo." + table, column);
        assertThat(((Number) definition.get("max_length")).intValue()).isEqualTo(maxLength);
        assertThat(definition.get("is_nullable")).isEqualTo(nullable);
    }

    private void insertValue(long ticketId, long formId, long fieldId, String raw) {
        jdbc.update("""
                INSERT INTO dbo.ticket_field_values (ticket_id, form_version_id, field_definition_id, value_json)
                VALUES (?, ?, ?, ?)
                """, ticketId, formId, fieldId, raw);
    }

    private void insertAttachment(long ticketId, long formId, Long fieldId, long uploaderId,
            String type, String key, long size) {
        jdbc.update("""
                INSERT INTO dbo.ticket_attachments (ticket_id, form_version_id, field_definition_id,
                    uploaded_by, attachment_type, storage_key, original_name, content_type, size_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ticketId, formId, fieldId, uploaderId, type, key, "ảnh.png", "image/png", size);
    }

    private void insertHistory(long ticketId, long actorId, String from, String to) {
        jdbc.update("""
                INSERT INTO dbo.ticket_status_history (ticket_id, from_status, to_status, transition_action, changed_by)
                VALUES (?, ?, ?, 'SUBMIT', ?)
                """, ticketId, from, to, actorId);
    }

    private Fixture fixture(String suffix) {
        AppUser reporter = persist(UserFixtures.builder().email("sql-support-reporter-" + suffix + "@example.test").build());
        AppUser actor = persist(UserFixtures.builder().email("sql-support-actor-" + suffix + "@example.test").build());
        IncidentCategory category = persist(new IncidentCategory("SQL-SUPPORT-" + suffix, "Supporting category"));
        CategoryFormVersion form = persist(new CategoryFormVersion(category, 1));
        Location location = persist(new Location("SQL-SUPPORT-" + suffix, "Supporting room", LocationType.ROOM, null));
        Ticket ticket = persist(new Ticket("NX-2026-SQL-SP-" + suffix, reporter, category, form,
                location, null, "Supporting ticket", "Description", Priority.NORMAL));
        FieldDefinition image = persist(new FieldDefinition(form, "photo", "Ảnh hiện trường", DynamicFieldType.IMAGE, 1));
        FieldDefinition answer = persist(new FieldDefinition(form, "text_answer", "Câu trả lời", DynamicFieldType.TEXT, 2));
        return new Fixture(ticket, reporter, actor, category, form, location, image, answer);
    }

    private Ticket otherTicket(Fixture f, String suffix) {
        return persist(new Ticket("NX-2026-SQL-SP-" + suffix, f.reporter(), f.category(), f.form(),
                f.location(), null, "Other ticket", "Description", Priority.NORMAL));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    private TicketAttachment attachment(Fixture f, FieldDefinition field, AttachmentType type,
            String key, OffsetDateTime time) {
        return new TicketAttachment(f.ticket(), field, f.actor(), type,
                key, "../ảnh.png", "image/png", 2_147_483_648L, time);
    }

    private record Fixture(Ticket ticket, AppUser reporter, AppUser actor, IncidentCategory category,
            CategoryFormVersion form, Location location, FieldDefinition image, FieldDefinition answer) {
    }
}
