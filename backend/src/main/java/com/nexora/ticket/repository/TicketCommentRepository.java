package com.nexora.ticket.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.nexora.ticket.document.TicketCommentDocument;

public interface TicketCommentRepository extends MongoRepository<TicketCommentDocument, String> {

    Optional<TicketCommentDocument> findByLegacySqlId(Long legacySqlId);

    List<TicketCommentDocument> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
