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
import com.nexora.ticket.entity.TicketStatus;

/**
 * Strict query contract for technician assigned work.
 * Rejects unsupported, duplicate, empty, or invalid parameters.
 */
public record TechnicianAssignedTicketsQuery(
        Pageable pageable, TicketStatus status, Priority priority, Long locationId) {

    public static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "createdAt", "createdAt", "updatedAt", "updatedAt", "ticketNumber", "ticketNumber");

    private static final Set<String> PARAMETERS = Set.of(
            "page", "size", "sort", "status", "priority", "locationId");

    private static final Set<TicketStatus> ALLOWED_STATUSES = Set.of(
            TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);

    public static TechnicianAssignedTicketsQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if (!PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter: " + key);
            }
            if (values.size() != 1) {
                if (key.equals("sort")) throw new UnsupportedSortException("Only one sort is supported.");
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once.");
            }
            String value = values.getFirst();
            if (value == null || value.trim().isEmpty()) {
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameter cannot be blank.");
                }
                if (key.equals("sort")) {
                    throw new UnsupportedSortException("Sort parameter cannot be blank.");
                }
                throw new UnsupportedFilterException("Filter parameter cannot be blank.");
            }
        });

        Integer page = integer("page", parameters.getFirst("page"));
        Integer size = integer("size", parameters.getFirst("size"));
        String sort = parameters.containsKey("sort") ? parameters.getFirst("sort") : "createdAt,desc";

        Pageable request = PageRequestFactory.create(page, size, List.of(sort), SORT_ALLOWLIST);
        if (request.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidPaginationException("Page offset exceeds the supported range.");
        }
        request = PageRequest.of(
                request.getPageNumber(),
                request.getPageSize(),
                request.getSort().and(Sort.by("id").ascending()));

        return new TechnicianAssignedTicketsQuery(
                request,
                statusFilter(parameters.getFirst("status")),
                priorityFilter(parameters.getFirst("priority")),
                locationFilter(parameters.getFirst("locationId")));
    }

    private static Integer integer(String field, String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException("Page and size must be non-negative integers within range.");
        }
    }

    private static TicketStatus statusFilter(String value) {
        if (value == null) return null;
        try {
            TicketStatus parsed = TicketStatus.valueOf(value);
            if (!ALLOWED_STATUSES.contains(parsed)) {
                throw new UnsupportedFilterException("Status filter must be ASSIGNED or IN_PROGRESS.");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedFilterException("Status filter must be ASSIGNED or IN_PROGRESS.");
        }
    }

    private static Priority priorityFilter(String value) {
        if (value == null) return null;
        try {
            return Priority.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedFilterException("Unsupported priority filter: " + value);
        }
    }

    private static Long locationFilter(String value) {
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
}
