package com.nexora.asset.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;
import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.exception.UnsupportedSortException;
import com.nexora.common.pagination.PageRequestFactory;
import com.nexora.common.validation.RequestParameterValidator;

import jakarta.persistence.criteria.Predicate;

/**
 * Strict public query contract for Asset listings.
 * Enforces allowed filter/sort parameters and prevents arbitrary input from reaching JPA.
 */
public record AssetListQuery(
        Pageable pageable,
        Long locationId,
        AssetStatus status
) {
    public static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "assetCode", "assetCode",
            "id", "id"
    );

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "page", "size", "sort", "locationId", "status"
    );

    public static AssetListQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if (!ALLOWED_PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter: " + key);
            }
            if (values.size() != 1) {
                if (key.equals("sort")) {
                    throw new UnsupportedSortException("Only one sort is supported.");
                }
                if (key.equals("page") || key.equals("size")) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once.");
            }
        });

        Integer page = parseInteger(parameters.getFirst("page"));
        Integer size = parseInteger(parameters.getFirst("size"));
        String sort = parameters.containsKey("sort") ? parameters.getFirst("sort") : "createdAt,desc";

        Pageable pageableRequest = PageRequestFactory.create(page, size, List.of(sort), SORT_ALLOWLIST);
        if (pageableRequest.getOffset() > Integer.MAX_VALUE) {
            throw new InvalidPaginationException("Page offset exceeds the supported range.");
        }
        pageableRequest = PageRequest.of(
                pageableRequest.getPageNumber(),
                pageableRequest.getPageSize(),
                pageableRequest.getSort().and(Sort.by("id").ascending())
        );

        Long locationId = parseLocation(parameters.getFirst("locationId"));
        AssetStatus status = parseEnumFilter("status", parameters.getFirst("status"), AssetStatus.class);

        return new AssetListQuery(pageableRequest, locationId, status);
    }

    public Specification<Asset> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (!value.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException("Page and size must be non-negative integers within range.");
        }
    }

    private static Long parseLocation(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (!value.matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new UnsupportedFilterException("Location ID must be a positive integer within range.");
        }
    }

    private static <E extends Enum<E>> E parseEnumFilter(String name, String value, Class<E> type) {
        RequestParameterValidator.requireAllowedFilter(name, value,
                Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet()));
        return value == null ? null : Enum.valueOf(type, value);
    }
}
