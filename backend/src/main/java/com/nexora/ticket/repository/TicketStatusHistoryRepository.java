package com.nexora.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.ticket.entity.TicketStatusHistory;

/**
 * Append history in the same transaction as the ticket update. Callers must not
 * update/delete historical records; generic delete methods are not workflow operations.
 */
public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, Long> {

    /** Oldest first, with ID breaking timestamp ties; empty if absent. */
    List<TicketStatusHistory> findByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
