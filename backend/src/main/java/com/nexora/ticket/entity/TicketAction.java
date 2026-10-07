package com.nexora.ticket.entity;

/** Explicit business actions that may move a ticket through the MVP lifecycle. */
public enum TicketAction {
    SUBMIT,
    BEGIN_REVIEW,
    REJECT,
    CANCEL_BEFORE_ASSIGNMENT,
    ASSIGN,
    START_WORK,
    UNASSIGN,
    REASSIGN,
    CANCEL_AFTER_ASSIGNMENT,
    RESOLVE,
    CLOSE,
    FORCE_CLOSE,
    REOPEN
}
