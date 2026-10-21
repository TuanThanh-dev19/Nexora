package com.nexora.ticket.testsupport;

import com.fasterxml.jackson.databind.*;
import com.nexora.category.entity.*;
import com.nexora.ticket.dto.request.CreateTicketRequest;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

/** Valid, database-free input fixture for consumers of the submission validator. */
public record TicketSubmissionFixtures(
        CategoryFormVersion form, List<FieldDefinition> fields, List<FieldOption> options) {
    public static TicketSubmissionFixtures create(
            DynamicFieldType type, JsonNode rules, boolean required) {
        var category = new IncidentCategory("SUBMISSION", "Submission fixture");
        ReflectionTestUtils.setField(category, "id", 1L);
        var form = new CategoryFormVersion(category, 1);
        ReflectionTestUtils.setField(form, "id", 2L);
        form.changeFormStatus(FormStatus.PUBLISHED);
        var field = new FieldDefinition(form, "answer", "Answer", type, 0);
        ReflectionTestUtils.setField(field, "id", 3L);
        field.changeRequired(required);
        field.changeValidationRules(rules.toString());
        var option = new FieldOption(field, "a", "Alpha", 0);
        var inactive = new FieldOption(field, "old", "Inactive", 1);
        inactive.changeActive(false);
        return new TicketSubmissionFixtures(form, List.of(field), List.of(option, inactive));
    }

    public CreateTicketRequest request(
            List<CreateTicketRequest.DynamicValue> answers,
            List<CreateTicketRequest.Attachment> attachments) {
        return new CreateTicketRequest(
                1L, 2L, 10L, null, "Title", "Description", answers, attachments);
    }

    public CreateTicketRequest request(JsonNode value) {
        return request(List.of(new CreateTicketRequest.DynamicValue("answer", value)), List.of());
    }
}
