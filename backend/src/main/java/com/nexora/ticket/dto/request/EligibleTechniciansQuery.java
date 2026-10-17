package com.nexora.ticket.dto.request;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.MultiValueMap;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.pagination.PaginationDefaults;
import com.nexora.common.validation.RequestParameterValidator;

public record EligibleTechniciansQuery(
        Pageable pageable,
        String query,
        EligibilityFilter eligibility) {

    public enum EligibilityFilter {
        ELIGIBLE,
        BLOCKED
    }

    private static final Set<String> PARAMETERS = Set.of("page", "size", "query", "eligibility");
    private static final Set<String> ALLOWED_ELIGIBILITY = Set.of("ELIGIBLE", "BLOCKED");

    public static EligibleTechniciansQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if (!PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter: " + key + ".");
            }
            if (values.size() != 1) {
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once.");
            }
            String value = values.getFirst();
            if (value == null || value.isBlank()) {
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Page and size must not be blank.");
                }
                throw new UnsupportedFilterException("Parameter '" + key + "' must not be blank.");
            }
        });

        Integer page = integer("page", parameters.getFirst("page"));
        Integer size = integer("size", parameters.getFirst("size"));
        int resolvedPage = page == null ? PaginationDefaults.DEFAULT_PAGE : page;
        int resolvedSize = size == null ? PaginationDefaults.DEFAULT_PAGE_SIZE : size;

        if (resolvedPage < 0) {
            throw new InvalidPaginationException("Page must be zero or greater.");
        }
        if (resolvedSize < 1 || resolvedSize > PaginationDefaults.MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "Size must be between 1 and " + PaginationDefaults.MAX_PAGE_SIZE + ".");
        }

        long offset = (long) resolvedPage * (long) resolvedSize;
        if (offset > Integer.MAX_VALUE) {
            throw new InvalidPaginationException("Page offset exceeds the supported range.");
        }

        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);

        String rawQuery = parameters.getFirst("query");
        String resolvedQuery = rawQuery != null ? rawQuery.trim() : null;

        String rawEligibility = parameters.getFirst("eligibility");
        EligibilityFilter eligibilityFilter = null;
        if (rawEligibility != null) {
            RequestParameterValidator.requireAllowedFilter("eligibility", rawEligibility, ALLOWED_ELIGIBILITY);
            eligibilityFilter = EligibilityFilter.valueOf(rawEligibility);
        }

        return new EligibleTechniciansQuery(pageable, resolvedQuery, eligibilityFilter);
    }

    private static Integer integer(String name, String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException(
                    "Page and size must be non-negative integers within range.");
        }
    }
}
