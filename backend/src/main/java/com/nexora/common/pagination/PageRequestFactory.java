package com.nexora.common.pagination;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedSortException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestFactory {

    private PageRequestFactory() {
    }

    public static Pageable create(Integer page, Integer size, List<String> sorts, Map<String, String> sortAllowlist) {
        int resolvedPage = page == null ? PaginationDefaults.DEFAULT_PAGE : page;
        int resolvedSize = size == null ? PaginationDefaults.DEFAULT_PAGE_SIZE : size;

        if (resolvedPage < 0) {
            throw new InvalidPaginationException("Page must be zero or greater.");
        }
        if (resolvedSize < 1 || resolvedSize > PaginationDefaults.MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "Size must be between 1 and " + PaginationDefaults.MAX_PAGE_SIZE + ".");
        }

        List<Sort.Order> orders = parseSorts(sorts, sortAllowlist);
        return orders.isEmpty()
                ? PageRequest.of(resolvedPage, resolvedSize)
                : PageRequest.of(resolvedPage, resolvedSize, Sort.by(orders));
    }

    private static List<Sort.Order> parseSorts(List<String> sorts, Map<String, String> sortAllowlist) {
        if (sorts == null || sorts.isEmpty()) {
            return List.of();
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String expression : sorts) {
            if (expression == null || expression.isBlank()) {
                throw new UnsupportedSortException("Sort value must not be blank.");
            }

            String[] parts = expression.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new UnsupportedSortException("Sort must use the format 'field' or 'field,direction'.");
            }

            String requestedField = parts[0].trim();
            String entityProperty = sortAllowlist.get(requestedField);
            if (entityProperty == null) {
                throw new UnsupportedSortException("Unsupported sort field: " + requestedField + ".");
            }

            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length == 2) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new UnsupportedSortException("Unsupported sort direction: " + parts[1].trim() + ".");
                }
            }
            orders.add(new Sort.Order(direction, entityProperty));
        }
        return orders;
    }
}
