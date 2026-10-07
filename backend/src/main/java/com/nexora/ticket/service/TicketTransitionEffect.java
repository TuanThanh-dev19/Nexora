package com.nexora.ticket.service;

/** Side effects expected from later application services; this contract does not execute them. */
public enum TicketTransitionEffect {
    WRITE_STATUS_HISTORY,
    CALCULATE_SLA_DUE_AT,
    CREATE_ACTIVE_ASSIGNMENT,
    SET_ACTIVE_ASSIGNMENT_ACCEPTED_AT,
    END_ACTIVE_ASSIGNMENT,
    REPLACE_ACTIVE_ASSIGNMENT_ATOMICALLY,
    END_ACTIVE_ASSIGNMENT_IF_PRESENT,
    SET_RESOLVED_AT,
    SET_CLOSED_AT,
    CLEAR_RESOLVED_AT
}
