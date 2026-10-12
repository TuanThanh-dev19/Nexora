package com.nexora.category.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.DynamicFieldType;
import jakarta.validation.Validator;

@Component
public class FormValidator {
    private final Validator validator;

    public FormValidator(Validator validator) { this.validator = validator; }

    public Map<String, String> errors(DraftRequest request, boolean publishing) {
        Map<String, String> errors = new LinkedHashMap<>();
        validator.validate(request).forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        if (request.fields() == null) return errors;
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < request.fields().size(); i++) {
            FieldRequest f = request.fields().get(i);
            String path = "fields[" + i + "]";
            if (f == null) continue;
            if (f.fieldKey() != null && !keys.add(f.fieldKey().toLowerCase(Locale.ROOT)))
                errors.put(path + ".fieldKey", "Field keys must be unique (case insensitive).");
            if (f.fieldType() == null || f.options() == null) continue;
            boolean select = f.fieldType() == DynamicFieldType.SELECT || f.fieldType() == DynamicFieldType.MULTI_SELECT;
            if (!select && !f.options().isEmpty()) errors.put(path + ".options", "Only select fields may have options.");
            if (publishing && select && f.options().stream().noneMatch(o -> o != null && o.active()))
                errors.put(path + ".options", "Select fields need at least one active option.");
            Set<String> values = new HashSet<>();
            for (int j = 0; j < f.options().size(); j++) {
                OptionRequest o = f.options().get(j);
                if (o != null && o.optionValue() != null && !values.add(o.optionValue().toLowerCase(Locale.ROOT)))
                    errors.put(path + ".options[" + j + "].optionValue", "Option values must be unique (case insensitive).");
            }
            rules(f.fieldType(), f.validationRules(), path + ".validationRules", errors);
        }
        return errors;
    }

    /** Explicit v1 allowlist: reject unknown or incompatible rules instead of dropping them. */
    public void rules(DynamicFieldType type, JsonNode rules, String path, Map<String, String> errors) {
        if (rules == null || !rules.isObject()) {
            errors.put(path, "Validation rules must be a JSON object.");
            return;
        }
        Set<String> allowed = switch (type) {
            case TEXT, TEXTAREA -> Set.of("minLength", "maxLength", "pattern");
            case NUMBER -> Set.of("min", "max", "step");
            case DATE, DATETIME -> Set.of("min", "max");
            case MULTI_SELECT -> Set.of("minItems", "maxItems");
            case IMAGE -> Set.of("minFiles", "maxFiles", "maxSizeBytes", "allowedTypes");
            default -> Set.of();
        };
        rules.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            String target = path + "." + key;
            if (!allowed.contains(key)) {
                errors.put(target, "Rule is not supported for " + type + ".");
            } else if (key.equals("pattern")) {
                try {
                    if (!value.isTextual() || value.textValue().length() > 500) throw new IllegalArgumentException();
                    Pattern.compile(value.textValue());
                } catch (IllegalArgumentException ex) { errors.put(target, "Use a valid regular expression (at most 500 characters)."); }
            } else if (key.equals("allowedTypes")) {
                if (!value.isArray() || value.isEmpty()) errors.put(target, "Choose at least one image MIME type.");
                else value.forEach(v -> {
                    if (!v.isTextual() || !Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(v.textValue()))
                        errors.put(target, "Supported types: image/jpeg, image/png, image/webp, image/gif.");
                });
            } else if (type == DynamicFieldType.DATE || type == DynamicFieldType.DATETIME) {
                try {
                    if (!value.isTextual()) throw new IllegalArgumentException();
                    if (type == DynamicFieldType.DATE) LocalDate.parse(value.textValue());
                    else LocalDateTime.parse(value.textValue());
                } catch (RuntimeException ex) { errors.put(target, "Use an ISO " + type + " value."); }
            } else if (type == DynamicFieldType.NUMBER) {
                if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || (key.equals("step") && value.decimalValue().signum() <= 0))
                    errors.put(target, "Use a finite number; step must be positive.");
            } else if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0
                    || (Set.of("maxFiles", "maxSizeBytes").contains(key) && value.intValue() == 0)) {
                errors.put(target, "Use a non-negative integer (file limits must be positive).");
            }
        });
        for (String[] pair : List.of(new String[]{"minLength", "maxLength"}, new String[]{"min", "max"},
                new String[]{"minItems", "maxItems"}, new String[]{"minFiles", "maxFiles"})) {
            JsonNode min = rules.get(pair[0]), max = rules.get(pair[1]);
            if (min == null || max == null || errors.keySet().stream().anyMatch(k -> k.startsWith(path))) continue;
            boolean reversed;
            if (type == DynamicFieldType.DATE) reversed = LocalDate.parse(min.asText()).isAfter(LocalDate.parse(max.asText()));
            else if (type == DynamicFieldType.DATETIME) reversed = LocalDateTime.parse(min.asText()).isAfter(LocalDateTime.parse(max.asText()));
            else reversed = min.decimalValue().compareTo(max.decimalValue()) > 0;
            if (reversed) errors.put(path + "." + pair[1], "Maximum must be greater than or equal to minimum.");
        }
    }
}
