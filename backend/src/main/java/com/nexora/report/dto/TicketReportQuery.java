package com.nexora.report.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

import com.nexora.common.domain.Priority;
import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.validation.RequestParameterValidator;

public record TicketReportQuery(
        LocalDate fromDate,
        LocalDate toDate,
        Long locationId,
        Priority priority,
        Pageable pageable) {

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "fromDate", "toDate", "locationId", "priority", "page", "size");

    public static TicketReportQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if (!ALLOWED_PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter.");
            }
            if (values.size() != 1) {
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once.");
            }
        });

        String fromDateStr = parameters.getFirst("fromDate");
        String toDateStr = parameters.getFirst("toDate");
        if (fromDateStr == null || fromDateStr.isBlank() || toDateStr == null || toDateStr.isBlank()) {
            throw new UnsupportedFilterException("fromDate and toDate parameters are required.");
        }

        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(fromDateStr);
            toDate = LocalDate.parse(toDateStr);
        } catch (DateTimeParseException exception) {
            throw new UnsupportedFilterException("fromDate and toDate must be valid dates in yyyy-MM-dd format.");
        }

        if (fromDate.isAfter(toDate)) {
            throw new UnsupportedFilterException("fromDate must be before or equal to toDate.");
        }

        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (daysBetween > 92) {
            throw new UnsupportedFilterException("Date range must not exceed 92 days.");
        }

        Long locationId = parseLocation(parameters.getFirst("locationId"));
        Priority priority = parsePriority(parameters.getFirst("priority"));

        int page = parsePage(parameters.getFirst("page"));
        int size = parseSize(parameters.getFirst("size"));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        return new TicketReportQuery(fromDate, toDate, locationId, priority, pageable);
    }

    private static int parsePage(String value) {
        if (value == null) return 0;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException("Page must be a non-negative integer.");
        }
    }

    private static int parseSize(String value) {
        if (value == null) return 20;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int size = Integer.parseInt(value);
            if (size < 1 || size > 100) {
                throw new InvalidPaginationException("Size must be between 1 and 100.");
            }
            return size;
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException("Size must be between 1 and 100.");
        }
    }

    private static Long parseLocation(String value) {
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

    private static Priority parsePriority(String value) {
        if (value == null) return null;
        RequestParameterValidator.requireAllowedFilter("priority", value,
                Arrays.stream(Priority.values()).map(Enum::name).collect(Collectors.toSet()));
        return Priority.valueOf(value);
    }
}

