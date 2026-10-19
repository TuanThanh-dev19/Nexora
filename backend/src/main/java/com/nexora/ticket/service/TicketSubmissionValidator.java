package com.nexora.ticket.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.nexora.category.entity.*;
import com.nexora.category.service.FormValidator;
import com.nexora.common.exception.ProblemTypes;
import com.nexora.common.storage.FileStorageValidator;
import com.nexora.ticket.dto.request.CreateTicketRequest;

import jakarta.validation.Validator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Pure submission checks. The caller owns the transaction and locked reference snapshot. */
@Component
public class TicketSubmissionValidator {
    private final Validator beans;
    private final FormValidator formRules;
    private final ObjectReader json;

    public TicketSubmissionValidator(
            Validator beans, FormValidator formRules, ObjectMapper mapper) {
        this.beans = beans;
        this.formRules = formRules;
        this.json =
                mapper.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .reader();
    }

    public void validateRequest(CreateTicketRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        beans.validate(request)
                .forEach(
                        v ->
                                errors.putIfAbsent(
                                        errorPath(request, v.getPropertyPath().toString()),
                                        v.getMessage()));
        reject(errors);
    }

    private String errorPath(CreateTicketRequest request, String path) {
        var dynamic = Pattern.compile("^dynamicValues\\[(\\d+)\\].*").matcher(path);
        if (dynamic.matches()) {
            var value = request.dynamicValues().get(Integer.parseInt(dynamic.group(1)));
            return value != null && value.fieldKey() != null && !value.fieldKey().isBlank()
                    ? "dynamicValues." + value.fieldKey()
                    : "dynamicValues";
        }
        return path.replaceFirst("^(attachments\\[\\d+\\]).*", "$1");
    }

    public Validated validate(
            CategoryFormVersion form,
            List<FieldDefinition> definitions,
            List<FieldOption> options,
            CreateTicketRequest request,
            List<MultipartFile> files) {
        Map<String, FieldDefinition> fields = new LinkedHashMap<>();
        Map<String, JsonNode> rules = new HashMap<>();
        int requiredFiles = 0;
        if (form.getMinAttachmentCount() > FileStorageValidator.MAX_FILE_COUNT
                || (form.isRequiresEquipment()
                        && form.getCategory().getAssetPolicy() == AssetPolicy.FORBIDDEN))
            throw TicketSubmissionException.incompatible("formVersionId");
        for (FieldDefinition field : definitions) {
            if (!field.isActive()) continue;
            if (fields.putIfAbsent(field.getFieldKey(), field) != null)
                throw TicketSubmissionException.incompatible("formVersionId");
            JsonNode rule = compatibleRules(field);
            rules.put(field.getFieldKey(), rule);
            if (field.getFieldType() == DynamicFieldType.IMAGE)
                requiredFiles +=
                        Math.max(field.isRequired() ? 1 : 0, rule.path("minFiles").asInt(0));
        }
        if (requiredFiles > 5) throw TicketSubmissionException.incompatible("formVersionId");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<FieldDefinition, JsonNode> answers = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (var value : request.dynamicValues()) {
            String path = "dynamicValues." + value.fieldKey();
            FieldDefinition field = fields.get(value.fieldKey());
            if (field == null
                    || !seen.add(value.fieldKey())
                    || field.getFieldType() == DynamicFieldType.IMAGE) {
                errors.put(path, "Unknown, inactive, duplicate or IMAGE field.");
                continue;
            }
            try {
                validateValue(field, value.value(), rules.get(value.fieldKey()), options);
                answers.put(field, value.value());
            } catch (IllegalArgumentException | java.time.DateTimeException ex) {
                errors.put(
                        path,
                        "Value has an invalid JSON type or does not satisfy the field rules.");
            }
        }
        for (var field : fields.values()) {
            if (field.getFieldType() != DynamicFieldType.IMAGE
                    && field.isRequired()
                    && !seen.contains(field.getFieldKey()))
                errors.put("dynamicValues." + field.getFieldKey(), "This field is required.");
        }
        if (files.size() > 5) errors.put("attachments", "At most five images are allowed.");
        if (files.size() < form.getMinAttachmentCount())
            errors.put("attachments", "Not enough evidence images.");
        if (request.attachments().size() != files.size())
            errors.put("attachments", "Map every file exactly once.");
        Map<Integer, FieldDefinition> linked = new HashMap<>();
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < request.attachments().size(); i++) {
            var attachment = request.attachments().get(i);
            String path = "attachments[" + i + "]";
            int index = attachment.fileIndex();
            if (index >= files.size() || !indices.add(index)) {
                errors.put(path, "Invalid or repeated fileIndex.");
                continue;
            }
            if (attachment.fieldKey() != null) {
                FieldDefinition field = fields.get(attachment.fieldKey());
                if (field == null || field.getFieldType() != DynamicFieldType.IMAGE)
                    errors.put(path, "Map images only to active IMAGE fields.");
                else linked.put(index, field);
            }
        }
        for (var field : fields.values()) {
            if (field.getFieldType() != DynamicFieldType.IMAGE) continue;
            long count = linked.values().stream().filter(f -> f == field).count();
            JsonNode rule = rules.get(field.getFieldKey());
            if (count < Math.max(field.isRequired() ? 1 : 0, rule.path("minFiles").asInt(0))
                    || count > rule.path("maxFiles").asInt(5))
                errors.put(
                        "dynamicValues." + field.getFieldKey(),
                        "Image count does not satisfy the field rules.");
        }
        // All MIME/size/signature checks precede storage writes.
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String path = "attachments[" + i + "]";
            if (file.getSize() > FileStorageValidator.MAX_FILE_SIZE)
                throw TicketSubmissionException.tooLarge(path);
            if (file.getContentType() == null
                    || !FileStorageValidator.ALLOWED_CONTENT_TYPES.contains(file.getContentType()))
                throw TicketSubmissionException.media(path);
            JsonNode rule =
                    linked.containsKey(i)
                            ? rules.get(linked.get(i).getFieldKey())
                            : json.createObjectNode();
            if (file.isEmpty()
                    || file.getOriginalFilename() == null
                    || file.getOriginalFilename().isBlank()
                    || file.getOriginalFilename().length() > 255
                    || file.getSize()
                            > rule.path("maxSizeBytes").asLong(FileStorageValidator.MAX_FILE_SIZE))
                errors.put(
                        path,
                        "Image is empty, has an invalid name, or exceeds the field size limit.");
            if (rule.has("allowedTypes")
                    && !contains(rule.get("allowedTypes"), file.getContentType()))
                errors.put(path, "Image MIME is not allowed by this field.");
            try (var input = file.getInputStream()) {
                byte[] signature = input.readNBytes(8);
                byte[] expected =
                        "image/png".equals(file.getContentType())
                                ? new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
                                : new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
                if (signature.length < expected.length
                        || !Arrays.equals(expected, Arrays.copyOf(signature, expected.length)))
                    errors.put(path, "Image signature does not match the declared MIME.");
            } catch (IOException ex) {
                throw TicketSubmissionException.invalid(path, "Cannot read uploaded image.");
            }
        }
        reject(errors);
        return new Validated(answers, linked);
    }

    private JsonNode compatibleRules(FieldDefinition field) {
        String path = "dynamicValues." + field.getFieldKey();
        JsonNode rules;
        try {
            rules = json.readTree(field.getValidationRules());
        } catch (IOException | IllegalArgumentException ex) {
            throw TicketSubmissionException.incompatible(path);
        }
        if (rules == null || !rules.isObject()) throw TicketSubmissionException.incompatible(path);
        Map<String, String> errors = new LinkedHashMap<>();
        formRules.rules(field.getFieldType(), rules, path, errors);
        if (!errors.isEmpty()) throw TicketSubmissionException.incompatible(path);
        return rules;
    }

    private void validateValue(
            FieldDefinition field, JsonNode value, JsonNode rule, List<FieldOption> options) {
        if (value == null || value.isNull()) throw new IllegalArgumentException();
        switch (field.getFieldType()) {
            case TEXT, TEXTAREA -> {
                if (!value.isTextual()) throw new IllegalArgumentException();
                String text = value.textValue();
                if ((field.isRequired() && text.isBlank())
                        || text.length() < rule.path("minLength").asInt(0)
                        || text.length() > rule.path("maxLength").asInt(Integer.MAX_VALUE)
                        || (rule.has("pattern")
                                && !Pattern.matches(rule.get("pattern").textValue(), text)))
                    throw new IllegalArgumentException();
            }
            case NUMBER -> {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue()))
                    throw new IllegalArgumentException();
                BigDecimal number = value.decimalValue();
                if ((rule.has("min") && number.compareTo(rule.get("min").decimalValue()) < 0)
                        || (rule.has("max")
                                && number.compareTo(rule.get("max").decimalValue()) > 0))
                    throw new IllegalArgumentException();
                if (rule.has("step")
                        && number.subtract(
                                                rule.has("min")
                                                        ? rule.get("min").decimalValue()
                                                        : BigDecimal.ZERO)
                                        .remainder(rule.get("step").decimalValue())
                                        .signum()
                                != 0) throw new IllegalArgumentException();
            }
            case BOOLEAN -> {
                if (!value.isBoolean()) throw new IllegalArgumentException();
            }
            case SELECT, MULTI_SELECT -> {
                Set<String> allowed = new HashSet<>();
                options.stream()
                        .filter(
                                o ->
                                        o.isActive()
                                                && o.getFieldDefinition()
                                                        .getId()
                                                        .equals(field.getId()))
                        .forEach(o -> allowed.add(o.getOptionValue()));
                if (field.getFieldType() == DynamicFieldType.SELECT) {
                    if (!value.isTextual() || !allowed.contains(value.textValue()))
                        throw new IllegalArgumentException();
                } else {
                    if (!value.isArray()
                            || value.size()
                                    < Math.max(
                                            field.isRequired() ? 1 : 0,
                                            rule.path("minItems").asInt(0))
                            || value.size() > rule.path("maxItems").asInt(Integer.MAX_VALUE))
                        throw new IllegalArgumentException();
                    Set<String> seen = new HashSet<>();
                    for (JsonNode v : value)
                        if (!v.isTextual()
                                || !allowed.contains(v.textValue())
                                || !seen.add(v.textValue())) throw new IllegalArgumentException();
                }
            }
            case DATE -> {
                if (!value.isTextual()) throw new IllegalArgumentException();
                LocalDate date = LocalDate.parse(value.textValue());
                if ((rule.has("min") && date.isBefore(LocalDate.parse(rule.get("min").textValue())))
                        || (rule.has("max")
                                && date.isAfter(LocalDate.parse(rule.get("max").textValue()))))
                    throw new IllegalArgumentException();
            }
            case DATETIME -> {
                if (!value.isTextual()) throw new IllegalArgumentException();
                OffsetDateTime time = OffsetDateTime.parse(value.textValue());
                if ((rule.has("min")
                                && time.isBefore(OffsetDateTime.parse(rule.get("min").textValue())))
                        || (rule.has("max")
                                && time.isAfter(OffsetDateTime.parse(rule.get("max").textValue()))))
                    throw new IllegalArgumentException();
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private static boolean contains(JsonNode values, String value) {
        for (JsonNode item : values)
            if (item.isTextual() && item.textValue().equals(value)) return true;
        return false;
    }

    private static void reject(Map<String, String> errors) {
        if (!errors.isEmpty())
            throw new TicketSubmissionException(
                    HttpStatus.BAD_REQUEST,
                    ProblemTypes.VALIDATION_FAILED,
                    "One or more submission fields are invalid.",
                    errors);
    }

    public record Validated(
            Map<FieldDefinition, JsonNode> answers, Map<Integer, FieldDefinition> imageFields) {}
}
