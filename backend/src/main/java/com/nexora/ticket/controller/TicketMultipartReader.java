package com.nexora.ticket.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.nexora.ticket.dto.request.CreateTicketRequest;
import com.nexora.ticket.service.TicketSubmissionException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Component
public class TicketMultipartReader {
    private final ObjectReader reader;
    private final ObjectReader metadataReader;

    @SuppressWarnings("deprecation")
    public TicketMultipartReader(ObjectMapper mapper) {
        reader =
                mapper.copy()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readerFor(CreateTicketRequest.class);
        metadataReader = reader.forType(JsonNode.class);
    }

    public Submission read(HttpServletRequest request) throws IOException, ServletException {
        var parts = request.getParts();
        var tickets = parts.stream().filter(p -> p.getName().equals("ticket")).toList();
        if (tickets.size() != 1
                || parts.stream().anyMatch(p -> !Set.of("ticket", "files").contains(p.getName())))
            throw TicketSubmissionException.invalid(
                    "ticket", "Supply exactly one ticket part and optional files parts.");
        Part ticket = tickets.getFirst();
        try {
            if (ticket.getContentType() == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(
                            MediaType.parseMediaType(ticket.getContentType())))
                throw TicketSubmissionException.media("ticket");
        } catch (org.springframework.http.InvalidMediaTypeException ex) {
            throw TicketSubmissionException.media("ticket");
        }
        CreateTicketRequest dto;
        try (var input = ticket.getInputStream()) {
            dto = reader.readValue(input);
        } catch (com.fasterxml.jackson.core.JacksonException ex) {
            throw TicketSubmissionException.invalid(
                    "ticket", "Invalid ticket JSON, unknown property or incorrect JSON type.");
        }
        if (dto == null)
            throw TicketSubmissionException.invalid("ticket", "Ticket JSON must be an object.");
        return new Submission(
                dto,
                parts.stream()
                        .filter(p -> p.getName().equals("files"))
                        .map(PartFile::new)
                        .map(f -> (MultipartFile) f)
                        .toList());
    }

    public record Submission(CreateTicketRequest ticket, List<MultipartFile> files) {}

    public List<MultipartFile> readWorkResult(HttpServletRequest request)
            throws IOException, ServletException {
        var parts = request.getParts();
        var metadata = parts.stream().filter(p -> p.getName().equals("metadata")).toList();
        if (metadata.size() != 1
                || parts.stream().anyMatch(p -> !Set.of("metadata", "files").contains(p.getName())))
            throw TicketSubmissionException.invalid(
                    "metadata", "Supply exactly one metadata part and repeated files parts.");
        // The browser sends metadata as a JSON string, without an application/json part header.
        try (var input = metadata.getFirst().getInputStream()) {
            JsonNode value = metadataReader.readValue(input);
            if (value == null || !value.isObject() || value.size() != 1
                    || !value.path("attachmentType").isTextual()
                    || !value.path("attachmentType").textValue().equals("WORK_RESULT"))
                throw TicketSubmissionException.invalid(
                        "metadata", "Metadata must contain only attachmentType: WORK_RESULT.");
        } catch (com.fasterxml.jackson.core.JacksonException ex) {
            throw TicketSubmissionException.invalid("metadata", "Invalid metadata JSON.");
        }
        return parts.stream()
                .filter(p -> p.getName().equals("files"))
                .map(PartFile::new)
                .map(f -> (MultipartFile) f)
                .toList();
    }

    private record PartFile(Part part) implements MultipartFile {
        public String getName() {
            return part.getName();
        }

        public String getOriginalFilename() {
            return part.getSubmittedFileName();
        }

        public String getContentType() {
            return part.getContentType();
        }

        public boolean isEmpty() {
            return part.getSize() == 0;
        }

        public long getSize() {
            return part.getSize();
        }

        public byte[] getBytes() throws IOException {
            try (var input = getInputStream()) {
                return input.readAllBytes();
            }
        }

        public InputStream getInputStream() throws IOException {
            return part.getInputStream();
        }

        public void transferTo(File destination) throws IOException {
            try (var input = getInputStream()) {
                java.nio.file.Files.copy(input, destination.toPath());
            }
        }
    }
}
