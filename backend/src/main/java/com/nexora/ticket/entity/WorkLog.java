package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

@Entity
@Table(name = "work_logs", schema = "dbo")
public class WorkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false, updatable = false)
    private AppUser technician;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "log_type", nullable = false, length = 30, updatable = false)
    private WorkLogType logType;

    @Column(name = "work_description", nullable = false, columnDefinition = "NVARCHAR(MAX)", updatable = false)
    private String workDescription;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", updatable = false)
    private OffsetDateTime endedAt;

    @Column(name = "minutes_spent", updatable = false)
    private Integer minutesSpent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected WorkLog() {}

    public WorkLog(
            Ticket ticket,
            AppUser technician,
            WorkLogType logType,
            String workDescription,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Integer minutesSpent,
            OffsetDateTime createdAt) {
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        this.technician = Objects.requireNonNull(technician, "technician is required");
        this.logType = Objects.requireNonNull(logType, "logType is required");
        this.workDescription = Objects.requireNonNull(workDescription, "workDescription is required");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt is required");
        this.endedAt = endedAt;
        this.minutesSpent = minutesSpent;
        this.createdAt = createdAt != null ? createdAt : OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public AppUser getTechnician() {
        return technician;
    }

    public WorkLogType getLogType() {
        return logType;
    }

    public String getWorkDescription() {
        return workDescription;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public Integer getMinutesSpent() {
        return minutesSpent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
