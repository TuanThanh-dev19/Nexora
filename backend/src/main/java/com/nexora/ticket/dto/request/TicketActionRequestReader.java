package com.nexora.ticket.dto.request;

import java.io.IOException;
import java.util.Set;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

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

    private static JsonMappingException invalid(JsonParser parser, String field, String message) {
        JsonMappingException error = JsonMappingException.from(parser, message);
        error.prependPath(TicketActionRequestReader.class, field);
        return error;
    }
}
