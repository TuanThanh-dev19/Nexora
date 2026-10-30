package com.nexora.notification.event;

public record TicketAssignedNotificationEvent(
        Long ticketId,
        String ticketNumber,
        String ticketTitle,
        Long assignmentId,
        Long technicianId,
        Long assignedByUserId) {

    public String eventKey() {
        return "TICKET_ASSIGNED:" + ticketId + ":" + assignmentId;
    }
}

