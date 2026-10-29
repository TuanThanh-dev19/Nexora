package com.nexora.ticket.repository;

import java.util.ArrayList;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import com.nexora.common.domain.Priority;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.RoleCode;
import jakarta.persistence.criteria.Predicate;

public final class TicketSpecifications {
    private TicketSpecifications() {}

    public static Specification<Ticket> visibleTo(Long userId, Set<RoleCode> roles) {
        return (root, query, cb) -> {
            if (roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER)) return cb.conjunction();
            var access = new ArrayList<Predicate>();
            if (roles.contains(RoleCode.REQUESTER)) access.add(cb.equal(root.get("reporter").get("id"), userId));
            if (roles.contains(RoleCode.TECHNICIAN)) {
                var assignment = query.subquery(Long.class);
                var assigned = assignment.from(TicketAssignment.class);
                assignment.select(assigned.get("id")).where(
                        cb.equal(assigned.get("ticketId"), root.get("id")),
                        cb.equal(assigned.get("technicianId"), userId));
                access.add(cb.exists(assignment));
            }
            return cb.or(access.toArray(Predicate[]::new));
        };
    }

    public static Specification<Ticket> filters(TicketListQuery filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (filters.status() != null) predicates.add(cb.equal(root.get("status"), filters.status()));
            if (filters.priority() != null) predicates.add(cb.equal(root.get("priority"), filters.priority()));
            if (filters.locationId() != null) predicates.add(cb.equal(root.get("location").get("id"), filters.locationId()));
            if (filters.keyword() != null) {
                // Explicit escape is shared by H2 and SQL Server, including SQL Server bracket patterns.
                String pattern = "%" + filters.keyword().toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![").replace("]", "!]") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("ticketNumber")), pattern, '!'),
                        cb.like(cb.lower(root.get("title")), pattern, '!')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Ticket> currentTechnicianWork(
            Long technicianId, TicketStatus status, Priority priority, Long locationId) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            var subquery = query.subquery(Long.class);
            var assigned = subquery.from(TicketAssignment.class);
            subquery.select(assigned.get("id")).where(
                    cb.equal(assigned.get("ticketId"), root.get("id")),
                    cb.equal(assigned.get("technicianId"), technicianId),
                    cb.isNull(assigned.get("endedAt")));
            predicates.add(cb.exists(subquery));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(root.get("status").in(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));
            }

            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            if (locationId != null) {
                predicates.add(cb.equal(root.get("location").get("id"), locationId));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Ticket> id(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}
