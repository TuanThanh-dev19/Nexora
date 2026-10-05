package com.nexora.ticket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.entity.FieldDefinition;
import com.nexora.category.entity.FormStatus;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAttachment;
import com.nexora.ticket.entity.TicketFieldValue;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.workflow.TicketAction;
import com.nexora.ticket.workflow.TicketStatus;
import com.nexora.user.entity.AppUser;

import jakarta.persistence.PersistenceException;

@NexoraJpaTest
class TicketSupportingPersistenceRepositoryTest {

    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-06T08:30:00+07:00");

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private TicketFieldValueRepository values;
    @Autowired
    private TicketStatusHistoryRepository history;
    @Autowired
    private TicketAttachmentRepository attachments;
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("jsonValues")
    void preservesRawJsonAndItsCanonicalType(DynamicFieldType type, String raw, JsonNodeType nodeType)
            throws Exception {
        Fixture f = fixture("JSON");
        FieldDefinition field = field(f.form(), "answer", type, 0);
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
    void ordersValuesByFieldOrderThenFieldIdAndReadsArchivedForms() {
        Fixture f = fixture("ORDER");
        FieldDefinition later = field(f.form(), "later", DynamicFieldType.TEXT, 9);
        FieldDefinition first = field(f.form(), "first", DynamicFieldType.TEXT, 0);
        FieldDefinition tied = field(f.form(), "tied", DynamicFieldType.TEXT, 0);
        values.saveAndFlush(new TicketFieldValue(f.ticket(), tied, "\"tied\""));
        values.saveAndFlush(new TicketFieldValue(f.ticket(), later, "\"later\""));
        values.saveAndFlush(new TicketFieldValue(f.ticket(), first, "\"first\""));
        Ticket other = ticket(f, "OTHER");
        values.saveAndFlush(new TicketFieldValue(other, first, "\"other ticket\""));
        f.form().changePublishingMetadata(f.actor().getId(), TIME);
        f.form().changeFormStatus(FormStatus.ARCHIVED);
        entityManager.flush();
        entityManager.clear();

        var loaded = values.findByTicketIdOrderByFieldOrder(f.ticket().getId());
        assertThat(loaded).extracting(v -> v.getFieldDefinition().getId())
                .containsExactly(first.getId(), tied.getId(), later.getId());
        assertThat(loaded).allSatisfy(v -> {
            assertThat(Hibernate.isInitialized(v.getFieldDefinition())).isTrue();
            assertThat(Hibernate.isInitialized(v.getTicket())).isFalse();
        });
    }

    @Test
    void ordersHistoryByTimeThenIdAndPreservesInitialAndSameStatusEntries() {
        Fixture f = fixture("HISTORY");
        TicketStatusHistory newer = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW, TicketAction.BEGIN_REVIEW,
                "Đã nhận báo cáo", f.actor(), TIME.plusDays(1)));
        TicketStatusHistory initial = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                null, TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor(), TIME));
        TicketStatusHistory tied = history.saveAndFlush(new TicketStatusHistory(f.ticket(),
                TicketStatus.ASSIGNED, TicketStatus.ASSIGNED, TicketAction.REASSIGN,
                "Đổi kỹ thuật viên", f.actor(), TIME));
        history.saveAndFlush(new TicketStatusHistory(ticket(f, "OTHER-HISTORY"),
                null, TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor(), TIME));
        entityManager.clear();

        var loaded = history.findByTicketIdOrderByChangedAtAscIdAsc(f.ticket().getId());
        assertThat(loaded).extracting(TicketStatusHistory::getId)
                .containsExactly(initial.getId(), tied.getId(), newer.getId());
        assertThat(loaded.getFirst().getFromStatus()).isNull();
        assertThat(loaded.getFirst().getReason()).isNull();
        assertThat(loaded.get(1).getTransitionAction()).isEqualTo(TicketAction.REASSIGN);
        assertThat(loaded.get(1).getFromStatus()).isEqualTo(loaded.get(1).getToStatus());
        assertThat(loaded.getLast().getReason()).isEqualTo("Đã nhận báo cáo");
        assertThat(loaded.getLast().getChangedBy().getId()).isEqualTo(f.actor().getId());
    }

    @Test
    void filtersAndOrdersAttachmentMetadataIncludingImageWithoutScalarRow() {
        Fixture f = fixture("ATTACH");
        FieldDefinition image = field(f.form(), "photo", DynamicFieldType.IMAGE, 0);
        TicketAttachment evidence = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.REPORT_EVIDENCE, "generic-evidence", TIME.plusDays(1)));
        TicketAttachment linked = attachments.saveAndFlush(attachment(f, image,
                AttachmentType.REPORT_EVIDENCE, "field-evidence", TIME));
        TicketAttachment work = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.WORK_RESULT, "work-result", TIME));
        TicketAttachment comment = attachments.saveAndFlush(attachment(f, null,
                AttachmentType.COMMENT, "reserved-comment", TIME.plusDays(2)));
        attachments.saveAndFlush(new TicketAttachment(ticket(f, "OTHER-ATTACH"), null, f.actor(),
                AttachmentType.REPORT_EVIDENCE, "other-evidence", "other.png", "image/png", 1, TIME));
        entityManager.clear();

        assertThat(attachments.findByTicketIdOrderByCreatedAtAscIdAsc(f.ticket().getId()))
                .extracting(TicketAttachment::getId)
                .containsExactly(linked.getId(), work.getId(), evidence.getId(), comment.getId());
        var reports = attachments.findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
                f.ticket().getId(), AttachmentType.REPORT_EVIDENCE);
        assertThat(reports).extracting(TicketAttachment::getId)
                .containsExactly(linked.getId(), evidence.getId());
        TicketAttachment loaded = reports.getFirst();
        assertThat(loaded.getFieldDefinition().getId()).isEqualTo(image.getId());
        assertThat(loaded.getFormVersionId()).isEqualTo(f.form().getId());
        assertThat(loaded.getUploadedBy().getId()).isEqualTo(f.actor().getId());
        assertThat(loaded.getStorageKey()).isEqualTo("field-evidence");
        assertThat(loaded.getOriginalName()).isEqualTo("../ảnh.png");
        assertThat(loaded.getContentType()).isEqualTo("image/png");
        assertThat(loaded.getSizeBytes()).isEqualTo(2_147_483_648L);
        assertThat(values.findByTicketIdOrderByFieldOrder(f.ticket().getId())).isEmpty();
        assertThat(attachments.findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
                f.ticket().getId(), AttachmentType.WORK_RESULT))
                .singleElement().satisfies(a -> {
                    assertThat(a.getFieldDefinition()).isNull();
                    assertThat(a.getFormVersionId()).isEqualTo(f.form().getId());
                });
    }

    @Test
    void returnsEmptyListsForTicketWithoutChildren() {
        Fixture f = fixture("EMPTY");
        assertThat(values.findByTicketIdOrderByFieldOrder(f.ticket().getId())).isEmpty();
        assertThat(history.findByTicketIdOrderByChangedAtAscIdAsc(f.ticket().getId())).isEmpty();
        assertThat(attachments.findByTicketIdOrderByCreatedAtAscIdAsc(f.ticket().getId())).isEmpty();
        assertThat(attachments.findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
                f.ticket().getId(), AttachmentType.REPORT_EVIDENCE)).isEmpty();
    }

    @Test
    void deletingValuesAndAttachmentsPreservesAllReferences() {
        Fixture f = fixture("DELETE");
        FieldDefinition field = field(f.form(), "answer", DynamicFieldType.TEXT, 0);
        FieldDefinition image = field(f.form(), "photo", DynamicFieldType.IMAGE, 1);
        TicketFieldValue value = values.saveAndFlush(new TicketFieldValue(f.ticket(), field, "\"answer\""));
        TicketAttachment attachment = attachments.saveAndFlush(attachment(f, image,
                AttachmentType.REPORT_EVIDENCE, "delete-metadata", TIME));
        values.delete(value);
        attachments.delete(attachment);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Ticket.class, f.ticket().getId())).isNotNull();
        assertThat(entityManager.find(FieldDefinition.class, field.getId())).isNotNull();
        assertThat(entityManager.find(FieldDefinition.class, image.getId())).isNotNull();
        assertThat(entityManager.find(CategoryFormVersion.class, f.form().getId())).isNotNull();
        assertThat(entityManager.find(IncidentCategory.class, f.category().getId())).isNotNull();
        assertThat(entityManager.find(AppUser.class, f.actor().getId())).isNotNull();
        assertThat(entityManager.find(AppUser.class, f.reporter().getId())).isNotNull();
        assertThat(entityManager.find(Location.class, f.location().getId())).isNotNull();
    }

    @Test
    void enforcesUniqueTicketFieldPair() {
        Fixture f = fixture("UNIQUE-VALUE");
        FieldDefinition field = field(f.form(), "answer", DynamicFieldType.TEXT, 0);
        values.saveAndFlush(new TicketFieldValue(f.ticket(), field, "\"one\""));
        assertThatThrownBy(() -> values.saveAndFlush(new TicketFieldValue(f.ticket(), field, "\"two\"")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesGloballyUniqueStorageKey() {
        Fixture f = fixture("UNIQUE-KEY");
        attachments.saveAndFlush(attachment(f, null, AttachmentType.REPORT_EVIDENCE, "duplicate-key", TIME));
        assertThatThrownBy(() -> attachments.saveAndFlush(new TicketAttachment(ticket(f, "DUP-KEY"),
                null, f.actor(), AttachmentType.WORK_RESULT, "duplicate-key", "result.png", "image/png", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"value", "history", "attachment"})
    void blocksTicketDeletionWhileAChildReferencesIt(String kind) {
        Fixture f = fixture("PARENT");
        addChild(f, kind);
        entityManager.clear();
        assertThatThrownBy(() -> {
            entityManager.remove(entityManager.find(Ticket.class, f.ticket().getId()));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private void addChild(Fixture f, String kind) {
        switch (kind) {
            case "value" -> values.saveAndFlush(new TicketFieldValue(f.ticket(),
                    field(f.form(), "answer", DynamicFieldType.TEXT, 0), "\"answer\""));
            case "history" -> history.saveAndFlush(new TicketStatusHistory(f.ticket(), null,
                    TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, f.actor()));
            case "attachment" -> attachments.saveAndFlush(attachment(f, null,
                    AttachmentType.REPORT_EVIDENCE, "parent-attachment", TIME));
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private Fixture fixture(String suffix) {
        AppUser reporter = entityManager.persistAndFlush(UserFixtures.builder()
                .email("support-reporter-" + suffix + "@example.test").build());
        AppUser actor = entityManager.persistAndFlush(UserFixtures.builder()
                .email("support-actor-" + suffix + "@example.test").build());
        IncidentCategory category = entityManager.persistAndFlush(
                new IncidentCategory("SUPPORT-" + suffix, "Supporting category"));
        CategoryFormVersion form = entityManager.persistAndFlush(new CategoryFormVersion(category, 1));
        Location location = entityManager.persistAndFlush(
                new Location("SUPPORT-" + suffix, "Supporting room", LocationType.ROOM, null));
        Ticket ticket = entityManager.persistAndFlush(new Ticket("NX-2026-SP-" + suffix,
                reporter, category, form, location, null, "Supporting ticket", "Description", Priority.NORMAL));
        return new Fixture(ticket, reporter, actor, category, form, location);
    }

    private Ticket ticket(Fixture f, String suffix) {
        return entityManager.persistAndFlush(new Ticket("NX-2026-SP-" + suffix, f.reporter(),
                f.category(), f.form(), f.location(), null, "Other ticket", "Description", Priority.NORMAL));
    }

    private FieldDefinition field(CategoryFormVersion form, String key, DynamicFieldType type, int order) {
        return entityManager.persistAndFlush(new FieldDefinition(form, key, "Label " + key, type, order));
    }

    private TicketAttachment attachment(Fixture f, FieldDefinition field, AttachmentType type,
            String key, OffsetDateTime createdAt) {
        return new TicketAttachment(f.ticket(), field, f.actor(), type,
                key, "../ảnh.png", "image/png", 2_147_483_648L, createdAt);
    }

    private record Fixture(Ticket ticket, AppUser reporter, AppUser actor,
            IncidentCategory category, CategoryFormVersion form, Location location) {
    }
}
