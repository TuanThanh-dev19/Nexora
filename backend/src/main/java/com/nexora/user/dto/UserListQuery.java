package com.nexora.user.dto;

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

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.exception.UnsupportedSortException;
import com.nexora.common.pagination.PageRequestFactory;
import com.nexora.common.validation.RequestParameterValidator;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;

import jakarta.persistence.criteria.Predicate;

public record UserListQuery(
        Pageable pageable,
        UserStatus status,
        RoleCode role
) {
    public static final Map<String, String> SORT_ALLOWLIST = Map.of(
            "id", "id",
            "email", "email",
            "fullName", "fullName",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "page", "size", "sort", "status", "role"
    );

    public static UserListQuery from(MultiValueMap<String, String> parameters) {
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

        UserStatus status = parseEnumFilter("status", parameters.getFirst("status"), UserStatus.class);
        RoleCode role = parseEnumFilter("role", parameters.getFirst("role"), RoleCode.class);

        return new UserListQuery(pageableRequest, status, role);
    }

    public Specification<AppUser> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (role != null) {
                var roleJoin = root.join("roleAssignments").join("role");
                predicates.add(cb.equal(roleJoin.get("code"), role));
                if (query != null) {
                    query.distinct(true);
                }
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

    private static <E extends Enum<E>> E parseEnumFilter(String name, String value, Class<E> type) {
        RequestParameterValidator.requireAllowedFilter(name, value,
                Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet()));
        return value == null ? null : Enum.valueOf(type, value);
    }
}
