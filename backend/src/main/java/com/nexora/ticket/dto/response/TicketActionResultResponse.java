package com.nexora.ticket.dto.response;

import java.time.OffsetDateTime;

import com.nexora.ticket.entity.TicketStatus;

public record TicketActionResultResponse(
        Long id,
        TicketStatus status,
        Long version,
        CurrentAssignmentResponse currentAssignment) {

    public record CurrentAssignmentResponse(
            Long id,
            Long ticketId,
            TechnicianSummary technician,
            UserSummary assignedBy,
            OffsetDateTime assignedAt,
            OffsetDateTime acceptedAt,
            OffsetDateTime endedAt,
            String endReason,
            String overrideReason) {
    }

    public record TechnicianSummary(Long id, Long technicianId, String fullName) {
        public TechnicianSummary(Long id, String fullName) {
            this(id, id, fullName);
        }
    }

    public record UserSummary(Long id, String fullName) {
    }
}
