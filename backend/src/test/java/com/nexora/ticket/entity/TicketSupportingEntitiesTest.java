package com.nexora.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.FieldDefinition;
import com.nexora.user.entity.AppUser;

class TicketSupportingEntitiesTest {

    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-10-06T08:30:00+07:00");

    private final CategoryFormVersion form = mock(CategoryFormVersion.class);
    private final Ticket ticket = mock(Ticket.class);
    private final FieldDefinition field = mock(FieldDefinition.class);
    private final AppUser actor = mock(AppUser.class);

    TicketSupportingEntitiesTest() {
        when(form.getId()).thenReturn(10L);
        when(ticket.getId()).thenReturn(20L);
        when(ticket.getFormVersion()).thenReturn(form);
        when(field.getId()).thenReturn(30L);
        when(field.getFormVersion()).thenReturn(form);
        when(actor.getId()).thenReturn(40L);
    }

    @Test
    void requiresPersistedReferencesAndNonNullValue() {
        assertThatThrownBy(() -> new TicketFieldValue(null, field, "false"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ticket");
        assertThatThrownBy(() -> new TicketFieldValue(ticket, null, "false"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("fieldDefinition");
        assertThatThrownBy(() -> new TicketFieldValue(ticket, field, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("valueJson");
        when(field.getId()).thenReturn(null);
        assertThatThrownBy(() -> new TicketFieldValue(ticket, field, "false"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("persisted");
        when(ticket.getId()).thenReturn(null);
        assertThatThrownBy(() -> new TicketFieldValue(ticket, field, "false"))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ticket must be persisted");
    }

    @Test
    void rejectsCrossFormValuesAndAttachments() {
        CategoryFormVersion otherForm = mock(CategoryFormVersion.class);
        when(otherForm.getId()).thenReturn(11L);
        when(field.getFormVersion()).thenReturn(otherForm);

        assertThatThrownBy(() -> new TicketFieldValue(ticket, field, "\"answer\""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("formVersion");
        assertThatThrownBy(() -> attachment(field, "opaque-key", 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("formVersion");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveAttachmentSize(long size) {
        assertThatThrownBy(() -> attachment(null, "opaque-key", size))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sizeBytes");
    }

    @ParameterizedTest
    @MethodSource("invalidStorageKeys")
    void rejectsEmptyOrOversizedStorageKey(String key) {
        assertThatThrownBy(() -> attachment(null, key, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("storageKey");
    }

    static Stream<String> invalidStorageKeys() {
        return Stream.of("", "  \t", "k".repeat(501));
    }

    @Test
    void attachmentRequiresMetadataAndPersistedActor() {
        assertThatThrownBy(() -> new TicketAttachment(ticket, null, actor, null,
                "key", "name", "image/png", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> attachment(null, null, 1))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("storageKey");
        assertThatThrownBy(() -> new TicketAttachment(ticket, null, actor, AttachmentType.WORK_RESULT,
                "key", null, "image/png", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketAttachment(ticket, null, actor, AttachmentType.WORK_RESULT,
                "key", "name", null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketAttachment(ticket, null, null, AttachmentType.WORK_RESULT,
                "key", "name", "image/png", 1)).isInstanceOf(NullPointerException.class);
        when(actor.getId()).thenReturn(null);
        assertThatThrownBy(() -> attachment(null, "key", 1))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("uploadedBy must be persisted");
    }

    @Test
    void keepsOpaqueKeyDisplayFilenameAndOptionalFieldUnchanged() {
        TicketAttachment unlinked = attachment(null, "opaque-key", 1);
        TicketAttachment linked = attachment(field, "another-key", 2);

        assertThat(unlinked.getFormVersionId()).isEqualTo(10L);
        assertThat(unlinked.getFieldDefinition()).isNull();
        assertThat(unlinked.getStorageKey()).isEqualTo("opaque-key");
        assertThat(unlinked.getOriginalName()).isEqualTo("../ảnh.png");
        assertThat(unlinked.getCreatedAt()).isEqualTo(TIME);
        assertThat(linked.getFieldDefinition()).isSameAs(field);
        assertThat(linked.getFormVersionId()).isEqualTo(10L);
    }

    @Test
    void historyAllowsInitialAndSameStatusEntriesWithoutExecutingWorkflow() {
        TicketStatusHistory initial = new TicketStatusHistory(ticket, null,
                TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, actor, TIME);
        TicketStatusHistory reassigned = new TicketStatusHistory(ticket, TicketStatus.ASSIGNED,
                TicketStatus.ASSIGNED, TicketAction.REASSIGN, "Đổi kỹ thuật viên", actor, TIME);

        assertThat(initial.getFromStatus()).isNull();
        assertThat(initial.getReason()).isNull();
        assertThat(initial.getChangedAt()).isEqualTo(TIME);
        assertThat(reassigned.getFromStatus()).isEqualTo(reassigned.getToStatus());
        assertThat(reassigned.getTransitionAction()).isEqualTo(TicketAction.REASSIGN);
    }

    @Test
    void historyRequiresTargetActionActorAndTimestamp() {
        assertThatThrownBy(() -> new TicketStatusHistory(ticket, null, null,
                TicketAction.SUBMIT, null, actor, TIME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED,
                null, null, actor, TIME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED,
                TicketAction.SUBMIT, null, null, TIME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED,
                TicketAction.SUBMIT, null, actor, null)).isInstanceOf(NullPointerException.class);
        when(actor.getId()).thenReturn(null);
        assertThatThrownBy(() -> new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED,
                TicketAction.SUBMIT, null, actor, TIME))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("persisted");
    }

    @Test
    void defaultHistoryAndAttachmentTimestampsUseUtc() {
        TicketStatusHistory history = new TicketStatusHistory(ticket, null,
                TicketStatus.SUBMITTED, TicketAction.SUBMIT, null, actor);
        TicketAttachment attachment = new TicketAttachment(ticket, null, actor,
                AttachmentType.WORK_RESULT, "key", "ảnh.png", "image/png", 1);
        assertThat(history.getChangedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(attachment.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    private TicketAttachment attachment(FieldDefinition definition, String key, long size) {
        return new TicketAttachment(ticket, definition, actor, AttachmentType.REPORT_EVIDENCE,
                key, "../ảnh.png", "image/png", size, TIME);
    }
}
