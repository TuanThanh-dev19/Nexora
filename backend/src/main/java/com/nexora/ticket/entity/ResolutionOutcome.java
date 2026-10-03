package com.nexora.ticket.entity;

/** Canonical outcomes stored when a ticket is resolved. */
public enum ResolutionOutcome {
    FIXED,
    TEMPORARY_FIX,
    NO_FAULT_FOUND,
    DUPLICATE,
    UNRESOLVED
}
