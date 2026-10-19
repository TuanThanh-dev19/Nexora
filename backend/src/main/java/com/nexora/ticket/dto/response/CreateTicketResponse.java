package com.nexora.ticket.dto.response;

import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketStatus;

public record CreateTicketResponse(
        Long id, String ticketNumber, TicketStatus status, Long version) {
    public static CreateTicketResponse from(Ticket ticket) {
        return new CreateTicketResponse(
                ticket.getId(), ticket.getTicketNumber(), ticket.getStatus(), ticket.getVersion());
    }
}
