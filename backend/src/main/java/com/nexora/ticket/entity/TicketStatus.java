package com.nexora.ticket.entity;

/** Canonical ticket lifecycle states persisted by SQL Server and exposed by the API. */
public enum TicketStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    REOPENED,
    CLOSED,
    REJECTED,
    CANCELLED
}
