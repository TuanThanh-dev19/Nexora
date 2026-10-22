package com.nexora.ticket.dto.request;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.WorkLogType;

/** Strict parsing only for Ticket actions, without changing other API contracts. */
public final class TicketActionRequestReader {
    private TicketActionRequestReader() {}

    public static class Review extends JsonDeserializer<ReviewTicketRequest> {
        @Override
        public ReviewTicketRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, Set.of("version"));
            return new ReviewTicketRequest(integer(parser, body, "version"));
        }
    }

    public static class Start extends JsonDeserializer<StartTicketRequest> {
        @Override
        public StartTicketRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, Set.of("version"));
            return new StartTicketRequest(integer(parser, body, "version"));
        }
    }

    public static class Assign extends JsonDeserializer<AssignTicketRequest> {
        @Override
        public AssignTicketRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, Set.of("version", "technicianId"));
            return new AssignTicketRequest(integer(parser, body, "version"), integer(parser, body, "technicianId"));
        }
    }

    public static class AddWorkLog extends JsonDeserializer<AddWorkLogRequest> {
        private static final Set<String> ALLOWED = Set.of(
                "version", "logType", "content", "startedAt", "endedAt", "minutesSpent");

        @Override
        public AddWorkLogRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, ALLOWED);
            Long version = version(parser, body, "version");
            WorkLogType logType = logType(parser, body, "logType");
            String content = requiredTrimmedString(parser, body, "content");
            OffsetDateTime startedAt = offsetDateTime(parser, body, "startedAt", true);
            OffsetDateTime endedAt = offsetDateTime(parser, body, "endedAt", false);
            if (startedAt != null && endedAt != null && endedAt.isBefore(startedAt)) {
                throw invalid(parser, "endedAt", "EndedAt must be greater than or equal to startedAt.");
            }
            Integer minutesSpent = minutesSpent(parser, body, "minutesSpent");
            return new AddWorkLogRequest(version, logType, content, startedAt, endedAt, minutesSpent);
        }
    }

    public static class Resolve extends JsonDeserializer<ResolveTicketRequest> {
        private static final Set<String> ALLOWED = Set.of(
                "version", "resolutionOutcome", "resolutionSummary");

        @Override
        public ResolveTicketRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, ALLOWED);
            Long version = version(parser, body, "version");
            ResolutionOutcome outcome = resolutionOutcome(parser, body, "resolutionOutcome");
            String summary = resolutionSummary(parser, body, "resolutionSummary");
            return new ResolveTicketRequest(version, outcome, summary);
        }
    }

    public static class Close extends JsonDeserializer<CloseTicketRequest> {
        private static final Set<String> ALLOWED = Set.of("version", "reason");

        @Override
        public CloseTicketRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = body(parser, ALLOWED);
            Long version = version(parser, body, "version");
            String reason = optionalTrimmedString(parser, body, "reason", 1000);
            return new CloseTicketRequest(version, reason);
        }
    }

    private static JsonNode body(JsonParser parser, Set<String> allowed) throws IOException {
        JsonNode body = parser.getCodec().readTree(parser);
        if (parser.nextToken() != null) throw invalid(parser, "body", "Expected a single JSON object.");
        if (!body.isObject()) throw invalid(parser, "body", "Expected a JSON object.");
        var names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) throw invalid(parser, name, "Field is not supported.");
        }
        return body;
    }

    private static Long integer(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null; // Bean validation reports required fields.
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw invalid(parser, field, "Expected an integer within the supported range.");
        return value.longValue();
    }

    private static Long version(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            throw invalid(parser, field, "Ticket version is required.");
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalid(parser, field, "Ticket version must be non-negative.");
        }
        return value.longValue();
    }

    private static WorkLogType logType(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw invalid(parser, field, "Log type is required.");
        }
        try {
            return WorkLogType.valueOf(value.textValue());
        } catch (IllegalArgumentException ex) {
            throw invalid(parser, field, "Log type must be one of: DIAGNOSIS, PROGRESS, REPAIR, RESOLUTION, NO_FAULT_FOUND.");
        }
    }

    private static String requiredTrimmedString(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw invalid(parser, field, "Work log content is required.");
        }
        String text = value.textValue().trim();
        if (text.isEmpty()) {
            throw invalid(parser, field, "Work log content cannot be blank.");
        }
        return text;
    }

    private static OffsetDateTime offsetDateTime(JsonParser parser, JsonNode body, String field, boolean required)
            throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw invalid(parser, field, "StartedAt timestamp is required.");
            }
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(parser, field, "Expected an ISO-8601 timestamp string.");
        }
        String text = value.textValue();
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw invalid(parser, field, "Must be an ISO-8601 timestamp with explicit UTC zone (Z) or offset.");
        }
    }

    private static Integer minutesSpent(JsonParser parser, JsonNode body, String field) throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw invalid(parser, field, "Minutes spent must be a positive integer.");
        }
        return value.intValue();
    }

    private static ResolutionOutcome resolutionOutcome(JsonParser parser, JsonNode body, String field)
            throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw invalid(parser, field, "Resolution outcome is required.");
        }
        String text = value.textValue();
        if ("DUPLICATE".equals(text) || !Set.of("FIXED", "TEMPORARY_FIX", "NO_FAULT_FOUND", "UNRESOLVED").contains(text)) {
            throw invalid(parser, field, "Outcome must be one of: FIXED, TEMPORARY_FIX, NO_FAULT_FOUND, UNRESOLVED.");
        }
        return ResolutionOutcome.valueOf(text);
    }

    private static String resolutionSummary(JsonParser parser, JsonNode body, String field)
            throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw invalid(parser, field, "Resolution summary is required.");
        }
        String text = value.textValue().trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw invalid(parser, field, "Resolution summary must be between 1 and 1000 characters.");
        }
        return text;
    }

    private static String optionalTrimmedString(JsonParser parser, JsonNode body, String field, int maxLength)
            throws JsonMappingException {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(parser, field, "Expected a string value.");
        }
        String text = value.textValue().trim();
        if (text.length() > maxLength) {
            throw invalid(parser, field, "Field length cannot exceed " + maxLength + " characters.");
        }
        return text.isEmpty() ? null : text;
    }

    private static JsonMappingException invalid(JsonParser parser, String field, String message) {
        JsonMappingException error = JsonMappingException.from(parser, message);
        error.prependPath(TicketActionRequestReader.class, field);
        return error;
    }
}
