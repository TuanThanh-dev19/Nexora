package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.EquipmentType;
import com.nexora.category.entity.*;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.*;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.*;

class TicketResponseMapperTest {
    @Test
    void serializationExposesOnlyPublicReferencesAndEmptyCollections() throws Exception {
        var category = new IncidentCategory("TEST", "Category");
        var form = new CategoryFormVersion(category, 7);
        var field = new FieldDefinition(form, "image", "Bằng chứng", DynamicFieldType.IMAGE, 0);
        var ticket = new Ticket("TEST-1", UserFixtures.requester(), category, form,
                new Location("L", "Location", LocationType.CAMPUS, null), null, "Title", "Description", Priority.NORMAL);
        var dto = new TicketResponseMapper().detail(ticket, List.of(field), List.of(), List.of(), List.of(), List.of());
        var payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(dto);
        assertThat(dto.formVersion().versionNumber()).isEqualTo(7);
        assertThat(dto.dynamicValues()).isEmpty();
        assertThat(dto.statusHistory()).isEmpty();
        assertThat(dto.attachments()).isEmpty();
        assertThat(payload).doesNotContain("passwordHash", "email", "storageKey", "roleAssignments");
        assertThat(dto.asset()).isNull();
        assertThat(dto.slaDueAt()).isNull();
    }

    @Test
    void mapsStoredFieldsOptionsRawValuesHistoryAndPublicAttachmentMetadataInInputOrder() throws Exception {
        var reporter = UserFixtures.requester();
        ReflectionTestUtils.setField(reporter, "id", 10L);
        var category = new IncidentCategory("TEST", "Category");
        var form = new CategoryFormVersion(category, 7);
        ReflectionTestUtils.setField(form, "id", 20L);
        form.changeFormStatus(FormStatus.ARCHIVED);
        var choice = new FieldDefinition(form, "choice", "Lựa chọn", DynamicFieldType.MULTI_SELECT, 0);
        var number = new FieldDefinition(form, "number", "Số", DynamicFieldType.NUMBER, 1);
        var image = new FieldDefinition(form, "image", "Ảnh", DynamicFieldType.IMAGE, 2);
        ReflectionTestUtils.setField(choice, "id", 30L);
        ReflectionTestUtils.setField(number, "id", 31L);
        ReflectionTestUtils.setField(image, "id", 32L);
        var oldOption = new FieldOption(choice, "old", "Nhãn lịch sử", 0);
        oldOption.changeActive(false);
        var newOption = new FieldOption(choice, "new", "Nhãn mới", 1);
        var location = new Location("L", "Location", LocationType.CAMPUS, null);
        var asset = new Asset("A-1", new EquipmentType("E", "Equipment"), location);
        ReflectionTestUtils.setField(asset, "id", 40L);
        var timestamp = OffsetDateTime.parse("2026-01-02T03:04:05Z");
        var ticket = new Ticket("TEST-2", reporter, category, form, location, asset,
                "Title", "Description", Priority.HIGH, timestamp, null);
        ReflectionTestUtils.setField(ticket, "id", 50L);
        ReflectionTestUtils.setField(ticket, "version", 3L);
        var choiceValue = new TicketFieldValue(ticket, choice, "[\"old\", \"new\"]");
        var numberValue = new TicketFieldValue(ticket, number, "9007199254740993");
        var initial = new TicketStatusHistory(ticket, null, TicketStatus.SUBMITTED,
                TicketAction.SUBMIT, null, reporter, timestamp);
        var sameStatus = new TicketStatusHistory(ticket, TicketStatus.SUBMITTED, TicketStatus.SUBMITTED,
                TicketAction.REASSIGN, "Reason", reporter, timestamp.plusSeconds(1));
        var evidence = new TicketAttachment(ticket, image, reporter, AttachmentType.REPORT_EVIDENCE,
                "opaque-private-key", "../ảnh.png", "image/png", 123, timestamp);
        var result = new TicketAttachment(ticket, null, reporter, AttachmentType.WORK_RESULT,
                "another-private-key", "result.pdf", "application/pdf", 456, timestamp.plusSeconds(1));

        var dto = new TicketResponseMapper().detail(ticket, List.of(choice, number, image),
                List.of(oldOption, newOption), List.of(choiceValue, numberValue),
                List.of(initial, sameStatus), List.of(evidence, result));

        assertThat(dto.description()).isEqualTo("Description");
        assertThat(dto.version()).isEqualTo(3L);
        assertThat(dto.asset().id()).isEqualTo(40L);
        assertThat(dto.asset().assetCode()).isEqualTo("A-1");
        assertThat(dto.slaDueAt()).isEqualTo(timestamp);
        assertThat(dto.formVersion().id()).isEqualTo(20L);
        assertThat(dto.formVersion().formStatus()).isEqualTo(FormStatus.ARCHIVED);
        assertThat(dto.formVersion().fields()).extracting(field -> field.id()).containsExactly(30L, 31L, 32L);
        assertThat(dto.formVersion().fields().getFirst().options()).extracting(option -> option.value())
                .containsExactly("old", "new");
        assertThat(dto.formVersion().fields().getFirst().options().getFirst().label()).isEqualTo("Nhãn lịch sử");
        assertThat(dto.formVersion().fields().getLast().options()).isEmpty();
        assertThat(dto.dynamicValues()).extracting(value -> value.valueJson())
                .containsExactly("[\"old\", \"new\"]", "9007199254740993");
        assertThat(dto.statusHistory()).extracting(entry -> entry.changedAt())
                .containsExactly(timestamp, timestamp.plusSeconds(1));
        assertThat(dto.statusHistory().getFirst().fromStatus()).isNull();
        assertThat(dto.statusHistory().getLast().reason()).isEqualTo("Reason");
        assertThat(dto.statusHistory().getLast().changedBy().id()).isEqualTo(10L);
        assertThat(dto.attachments()).extracting(attachment -> attachment.fieldDefinitionId()).containsExactly(32L, null);
        assertThat(dto.attachments().getFirst().originalName()).isEqualTo("../ảnh.png");
        assertThat(dto.attachments().getFirst().sizeBytes()).isEqualTo(123);
        assertThat(dto.attachments().getLast().attachmentType()).isEqualTo(AttachmentType.WORK_RESULT);
        var payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(dto);
        assertThat(payload).doesNotContain("storageKey", "opaque-private-key", "another-private-key", "passwordHash", "email");
    }
}
