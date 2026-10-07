package com.nexora.ticket.dto.request;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

import com.nexora.common.domain.Priority;
import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.exception.UnsupportedSortException;
import com.nexora.common.pagination.PageRequestFactory;
import com.nexora.common.validation.RequestParameterValidator;
import com.nexora.ticket.entity.TicketStatus;

/** Strict public query contract. No arbitrary parameter reaches JPA. */
public record TicketListQuery(Pageable pageable, TicketStatus status, Priority priority, Long locationId) {
    public static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "createdAt", "createdAt", "updatedAt", "updatedAt", "ticketNumber", "ticketNumber");
    private static final Set<String> PARAMETERS = Set.of("page", "size", "sort", "status", "priority", "locationId");

    public static TicketListQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if (!PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter.");
            }
            if (values.size() != 1) {
                if (key.equals("sort")) throw new UnsupportedSortException("Only one sort is supported.");
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once.");
            }
        });
        Integer page = integer(parameters.getFirst("page"));
        Integer size = integer(parameters.getFirst("size"));
        String sort = parameters.containsKey("sort") ? parameters.getFirst("sort") : "createdAt,desc";
        Pageable request = PageRequestFactory.create(page, size, List.of(sort), SORT_ALLOWLIST);
        // JPA's setFirstResult takes an int; reject an unrepresentable offset before querying.
        if (request.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidPaginationException("Page offset exceeds the supported range.");
        }
        request = PageRequest.of(request.getPageNumber(), request.getPageSize(),
                request.getSort().and(Sort.by("id").ascending()));
        return new TicketListQuery(request,
                enumFilter("status", parameters.getFirst("status"), TicketStatus.class),
                enumFilter("priority", parameters.getFirst("priority"), Priority.class),
                location(parameters.getFirst("locationId")));
    }

    private static Integer integer(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException("Page and size must be non-negative integers within range.");
        }
    }

    private static Long location(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new UnsupportedFilterException("Location ID must be a positive integer within range.");
        }
    }

    private static <E extends Enum<E>> E enumFilter(String name, String value, Class<E> type) {
        RequestParameterValidator.requireAllowedFilter(name, value,
                Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet()));
        return value == null ? null : Enum.valueOf(type, value);
    }
}
