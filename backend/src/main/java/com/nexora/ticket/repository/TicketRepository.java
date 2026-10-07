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

    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "location", "asset", "formVersion"})
    Page<Ticket> findAll(Specification<Ticket> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "location", "asset", "formVersion"})
    Optional<Ticket> findOne(Specification<Ticket> specification);
}
