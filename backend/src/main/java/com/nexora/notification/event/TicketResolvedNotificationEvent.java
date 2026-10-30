package com.nexora.notification.event;

public record TicketResolvedNotificationEvent(
        Long ticketId,
        String ticketNumber,
        String ticketTitle,
        Long historyId,
        Long reporterId,
        Long resolvedByUserId,
        String outcome,
        String resolutionSummary) {

    public String eventKey() {
        return "TICKET_RESOLVED:" + ticketId + ":" + historyId;
    }
}

