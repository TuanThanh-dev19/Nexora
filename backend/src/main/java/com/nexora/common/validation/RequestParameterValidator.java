package com.nexora.common.validation;

import java.util.Set;

import com.nexora.common.exception.UnsupportedFilterException;

public final class RequestParameterValidator {

    private RequestParameterValidator() {
    }

    public static void requireAllowedFilter(String parameter, String value, Set<String> allowedValues) {
        if (value != null && !allowedValues.contains(value)) {
            throw new UnsupportedFilterException(
                    "Unsupported value '" + value + "' for filter '" + parameter + "'.");
        }
    }
}
