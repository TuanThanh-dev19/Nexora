package com.nexora.ticket.service;

/** Additional relationship an actor must have beyond holding the permitted role. */
public enum TicketActorScope {
    SELF_SUBMITTER,
    TICKET_OWNER,
    CURRENT_ACTIVE_ASSIGNEE,
    NO_TICKET_RELATION_REQUIRED
}
