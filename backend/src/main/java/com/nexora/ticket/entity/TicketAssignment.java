package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only assignment mapping for visibility; no assignment write use case. */
@Entity
@Immutable
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
    @Column(name = "accepted_at", updatable = false)
    private OffsetDateTime acceptedAt;
    @Column(name = "ended_at", updatable = false)
    private OffsetDateTime endedAt;
    @Nationalized
    @Column(name = "end_reason", length = 500, updatable = false)
    private String endReason;
    @Nationalized
    @Column(name = "override_reason", length = 1000, updatable = false)
    private String overrideReason;
    protected TicketAssignment() {}
}
