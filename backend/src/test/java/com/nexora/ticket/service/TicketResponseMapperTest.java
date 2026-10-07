package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.category.entity.*;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.*;
import com.nexora.testsupport.fixture.UserFixtures;
import com.nexora.ticket.entity.Ticket;

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
}
