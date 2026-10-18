package com.nexora.user.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.exception.UnsupportedSortException;
import com.nexora.common.pagination.PaginationDefaults;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.UserRole;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public record TechnicianListQuery(
        Pageable pageable,
        String query,
        Boolean available
) {
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "page", "size", "query", "available"
    );

    public static TechnicianListQuery from(MultiValueMap<String, String> parameters) {
        parameters.forEach((key, values) -> {
            if ("sort".equals(key)) {
                throw new UnsupportedSortException("Client-supplied sorting is not supported.");
            }
            if (!ALLOWED_PARAMETERS.contains(key)) {
                throw new UnsupportedFilterException("Unsupported query parameter: " + key);
            }
            if (values.size() != 1) {
                if ("page".equals(key) || "size".equals(key)) {
                    throw new InvalidPaginationException("Pagination parameters must occur once.");
                }
                throw new UnsupportedFilterException("Filter parameters must occur once: " + key);
            }
        });

        Integer page = parseInteger(parameters.getFirst("page"), "page");
        int resolvedPage = page == null ? PaginationDefaults.DEFAULT_PAGE : page;
        if (resolvedPage < 0) {
            throw new InvalidPaginationException("Page must be zero or greater.");
        }

        Integer size = parseInteger(parameters.getFirst("size"), "size");
        int resolvedSize = size == null ? PaginationDefaults.DEFAULT_PAGE_SIZE : size;
        if (resolvedSize < 1 || resolvedSize > PaginationDefaults.MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "Size must be between 1 and " + PaginationDefaults.MAX_PAGE_SIZE + ".");
        }

        long offset = (long) resolvedPage * (long) resolvedSize;
        if (offset > Integer.MAX_VALUE) {
            throw new InvalidPaginationException("Page offset exceeds the supported range.");
        }

        Sort sort = Sort.by(Sort.Order.asc("user.fullName"), Sort.Order.asc("technicianId"));
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, sort);

        String searchQuery = null;
        if (parameters.containsKey("query")) {
            String rawQuery = parameters.getFirst("query");
            if (rawQuery == null || rawQuery.trim().isEmpty()) {
                throw new UnsupportedFilterException("Query must not be empty or blank.");
            }
            searchQuery = rawQuery.trim();
        }

        Boolean availableFilter = null;
        if (parameters.containsKey("available")) {
            String rawAvailable = parameters.getFirst("available");
            if (rawAvailable == null || rawAvailable.isBlank()) {
                throw new UnsupportedFilterException("Available filter must not be blank.");
            }
            if ("true".equalsIgnoreCase(rawAvailable)) {
                availableFilter = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(rawAvailable)) {
                availableFilter = Boolean.FALSE;
            } else {
                throw new UnsupportedFilterException("Invalid boolean value for available filter: " + rawAvailable);
            }
        }

        return new TechnicianListQuery(pageable, searchQuery, availableFilter);
    }

    public Specification<TechnicianProfile> toSpecification() {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Role requirement: Associated user must have TECHNICIAN role via EXISTS subquery
            Subquery<Integer> roleSubquery = criteriaQuery.subquery(Integer.class);
            Root<UserRole> userRoleRoot = roleSubquery.from(UserRole.class);
            roleSubquery.select(cb.literal(1))
                    .where(
                            cb.equal(userRoleRoot.get("user"), root.get("user")),
                            cb.equal(userRoleRoot.get("role").get("code"), RoleCode.TECHNICIAN)
                    );
            predicates.add(cb.exists(roleSubquery));

            // 2. Availability filter
            if (available != null) {
                predicates.add(cb.equal(root.get("available"), available));
            }

            // 3. Search query filter on fullName or email (case-insensitive substring)
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
                Path<String> fullName = root.get("user").get("fullName");
                Path<String> email = root.get("user").get("email");
                predicates.add(cb.or(
                        cb.like(cb.lower(fullName), pattern),
                        cb.like(cb.lower(email), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Integer parseInteger(String value, String paramName) {
        if (value == null) {
            return null;
        }
        if (!value.matches("-?[0-9]+")) {
            throw new InvalidPaginationException(paramName + " must be a valid integer.");
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException(paramName + " exceeds the supported integer range.");
        }
    }
}
