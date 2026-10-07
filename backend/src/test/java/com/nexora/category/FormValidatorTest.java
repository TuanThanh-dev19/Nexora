package com.nexora.category;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.service.FormValidator;
import jakarta.validation.Validation;

class FormValidatorTest {
    final FormValidator validator = new FormValidator(Validation.buildDefaultValidatorFactory().getValidator());
    final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @CsvSource(value = {
        "TEXT|[]", "TEXT|{\"unknown\":1}", "TEXT|{\"minLength\":-1}", "TEXT|{\"minLength\":8,\"maxLength\":3}",
        "TEXT|{\"pattern\":\"[\"}", "NUMBER|{\"min\":\"1\"}", "NUMBER|{\"step\":0}", "NUMBER|{\"min\":10,\"max\":5}",
        "BOOLEAN|{\"min\":0}", "DATE|{\"min\":\"2026-02-30\"}", "DATETIME|{\"min\":\"yesterday\"}",
        "MULTI_SELECT|{\"minItems\":3,\"maxItems\":2}", "IMAGE|{\"allowedTypes\":[]}", "IMAGE|{\"allowedTypes\":[\"text/html\"]}", "IMAGE|{\"maxFiles\":0}"
    }, delimiter = '|')
    void rejectsUnsupportedOrInvalidRules(DynamicFieldType type, String rules) throws Exception {
        Map<String, String> errors = new HashMap<>();
        validator.rules(type, json.readTree(rules), "rules", errors);
        assertThat(errors).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
        "TEXT|{\"minLength\":1,\"maxLength\":80,\"pattern\":\"^[A-Z]+$\"}", "NUMBER|{\"min\":-10.5,\"max\":100,\"step\":0.5}",
        "DATE|{\"min\":\"2026-01-01\",\"max\":\"2026-12-31\"}", "DATETIME|{\"min\":\"2026-10-07T12:30:00\"}",
        "IMAGE|{\"minFiles\":1,\"maxFiles\":3,\"allowedTypes\":[\"image/png\"]}", "BOOLEAN|{}", "SELECT|{}"
    }, delimiter = '|')
    void acceptsSupportedRules(DynamicFieldType type, String rules) throws Exception {
        Map<String, String> errors = new HashMap<>();
        validator.rules(type, json.readTree(rules), "rules", errors);
        assertThat(errors).isEmpty();
    }

    @Test void detectsBlankDuplicateKeysOptionsAndNegativeAttachments() throws Exception {
        FieldRequest f = new FieldRequest("duplicate", "Label", DynamicFieldType.TEXT, null, null, true, true, 0,
                json.readTree("{}"), List.of(new OptionRequest("same", "A", 0, true), new OptionRequest("SAME", "B", 1, false)));
        FieldRequest blank = new FieldRequest("", "", DynamicFieldType.SELECT, null, null, false, true, 2, json.readTree("{}"), List.of());
        Map<String, String> errors = validator.errors(new DraftRequest(0L, false, -1, null, null, List.of(f, f, blank)), true);
        assertThat(errors).containsKeys("minAttachments", "fields[1].fieldKey", "fields[0].options", "fields[0].options[1].optionValue", "fields[2].fieldKey", "fields[2].options");
    }
}
