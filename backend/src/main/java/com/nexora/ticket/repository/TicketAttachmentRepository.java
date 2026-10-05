package com.nexora.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.TicketAttachment;

/** Persistence of metadata only; this repository never reads or writes stored files. */
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    /** Oldest first, with ID breaking timestamp ties; empty if absent. */
    List<TicketAttachment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    /** Filters by ticket and purpose, ordered by creation time then ID ascending. */
    List<TicketAttachment> findByTicketIdAndAttachmentTypeOrderByCreatedAtAscIdAsc(
            Long ticketId, AttachmentType attachmentType);
}
