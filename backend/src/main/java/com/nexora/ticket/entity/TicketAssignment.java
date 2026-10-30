package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.Nationalized;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_assignments", schema = "dbo")
public class TicketAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    // References technician_profiles.technician_id, which is also the user's ID.
    @Column(name = "technician_id", nullable = false, updatable = false)
    private Long technicianId;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private Long assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Nationalized
    @Column(name = "end_reason", length = 1000)
    private String endReason;

    @Nationalized
    @Column(name = "override_reason", length = 1000, updatable = false)
    private String overrideReason;

    protected TicketAssignment() {}

    public TicketAssignment(Long ticketId, Long technicianId, Long assignedBy, OffsetDateTime assignedAt) {
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId is required");
        this.technicianId = Objects.requireNonNull(technicianId, "technicianId is required");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy is required");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt is required");
    }

    public void end(OffsetDateTime endedAt, String endReason) {
        Objects.requireNonNull(endedAt, "endedAt is required");
        if (this.endedAt != null || endedAt.isBefore(assignedAt)
                || (acceptedAt != null && endedAt.isBefore(acceptedAt)))
            throw new IllegalStateException("Assignment cannot be ended at this time.");
        this.endedAt = endedAt;
        this.endReason = endReason;
    }

    public void accept(OffsetDateTime acceptedAt) {
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
        if (this.acceptedAt != null || endedAt != null || acceptedAt.isBefore(assignedAt))
            throw new IllegalStateException("Assignment cannot be accepted at this time.");
        this.acceptedAt = acceptedAt;
    }

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getTechnicianId() { return technicianId; }
    public Long getAssignedBy() { return assignedBy; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }
    public String getOverrideReason() { return overrideReason; }
}
