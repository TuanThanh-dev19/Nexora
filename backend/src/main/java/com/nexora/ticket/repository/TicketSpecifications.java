package com.nexora.ticket.repository;

import java.util.ArrayList;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAssignment;
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
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Ticket> id(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}
