package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.nexora.ticket.workflow.TicketAction;
import com.nexora.ticket.workflow.TicketStatus;
import com.nexora.user.entity.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Append-only SQL history of a ticket action.
 *
 * <p>The later workflow service must validate the transition and save this row
 * in the same transaction as the ticket update. This entity executes no workflow
 * and must not be written through an AFTER_COMMIT audit listener.
 */
@Entity
@Table(name = "ticket_status_history", schema = "dbo")
public class TicketStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 30, updatable = false)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private TicketStatus toStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transition_action", nullable = false, length = 40, updatable = false)
    private TicketAction transitionAction;

    @Nationalized
    @Column(length = 1000, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false, updatable = false)
    private AppUser changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    protected TicketStatusHistory() {
    }

    public TicketStatusHistory(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus,
            TicketAction transitionAction, String reason, AppUser changedBy) {
        this(ticket, fromStatus, toStatus, transitionAction, reason, changedBy,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** A null source represents initial submission; same-status actions are valid history. */
    public TicketStatusHistory(Ticket ticket, TicketStatus fromStatus, TicketStatus toStatus,
            TicketAction transitionAction, String reason, AppUser changedBy, OffsetDateTime changedAt) {
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(ticket.getId(), "ticket must be persisted before adding history");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus is required");
        this.transitionAction = Objects.requireNonNull(transitionAction, "transitionAction is required");
        this.reason = reason;
        this.changedBy = Objects.requireNonNull(changedBy, "changedBy is required");
        Objects.requireNonNull(changedBy.getId(), "changedBy must be persisted before adding history");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt is required");
    }

    @PrePersist
    void initializeChangedAt() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public TicketAction getTransitionAction() {
        return transitionAction;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getChangedBy() {
        return changedBy;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return "TicketStatusHistory{id=" + id + ", fromStatus=" + fromStatus
                + ", toStatus=" + toStatus + ", changedAt=" + changedAt + "}";
    }
}
