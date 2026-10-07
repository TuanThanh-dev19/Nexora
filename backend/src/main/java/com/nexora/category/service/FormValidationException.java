package com.nexora.category.service;

import java.util.Map;
import com.nexora.common.exception.BusinessRuleException;

public class FormValidationException extends BusinessRuleException {
    private final Map<String, String> fieldErrors;

    public FormValidationException(Map<String, String> fieldErrors) {
        super("The form is invalid. Correct the indicated fields and retry.");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
