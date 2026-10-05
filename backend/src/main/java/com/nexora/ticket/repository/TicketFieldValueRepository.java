package com.nexora.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.ticket.entity.TicketFieldValue;

public interface TicketFieldValueRepository extends JpaRepository<TicketFieldValue, Long> {

    /** Returns values with field metadata, ordered by display order then field ID; empty if absent. */
    @Query("""
            select v from TicketFieldValue v
            join fetch v.fieldDefinition f
            where v.ticket.id = :ticketId
            order by f.displayOrder asc, f.id asc
            """)
    List<TicketFieldValue> findByTicketIdOrderByFieldOrder(@Param("ticketId") Long ticketId);
}
