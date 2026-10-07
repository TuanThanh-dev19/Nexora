package com.nexora.ticket.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.persistence.SqlReferenceValidator;
import com.nexora.ticket.document.TicketCommentDocument;
import com.nexora.ticket.repository.TicketCommentRepository;

@Service
public class TicketCommentService {

    private final TicketCommentRepository repository;
    private final SqlReferenceValidator referenceValidator;

    public TicketCommentService(TicketCommentRepository repository,
            SqlReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    public TicketCommentDocument create(Long ticketId, Long authorId, String body, String visibility) {
        referenceValidator.requireTicket(ticketId);
        referenceValidator.requireUser(authorId);

        if (body == null || body.isBlank() || body.length() > 2000) {
            throw new BusinessRuleException("Comment body must contain between 1 and 2000 characters");
        }
        String normalizedVisibility = visibility == null
                ? "PUBLIC"
                : visibility.toUpperCase(Locale.ROOT);
        if (!normalizedVisibility.equals("PUBLIC") && !normalizedVisibility.equals("INTERNAL")) {
            throw new BusinessRuleException("Comment visibility must be PUBLIC or INTERNAL");
        }

        return repository.save(new TicketCommentDocument(
                null, null, ticketId, authorId, body, normalizedVisibility, Instant.now(), null));
    }
}
