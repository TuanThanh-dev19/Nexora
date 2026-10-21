package com.nexora.ticket.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nexora.ticket.entity.Ticket;

/** Persistence access for the core ticket record. */
public interface TicketRepository
        extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "location", "asset", "formVersion"})
    Page<Ticket> findAll(Specification<Ticket> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "location", "asset", "formVersion"})
    Optional<Ticket> findOne(Specification<Ticket> specification);
}
