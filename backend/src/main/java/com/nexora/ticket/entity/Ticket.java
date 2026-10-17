package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.nexora.asset.entity.Asset;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.Location;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Skill;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Central persisted ticket record.
 *
 * <p>The aggregate deliberately exposes only narrow, unidirectional references.
 * Workflow children such as assignments, history, field values, and attachments
 * are queried through their own repositories instead of being loaded with every
 * ticket.
 */
@Entity
@Table(
        name = "tickets",
        schema = "dbo",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_tickets_ticket_number",
                        columnNames = "ticket_number"),
                @UniqueConstraint(
                        name = "uq_tickets_id_form_version",
                        columnNames = {"id", "form_version_id"})
        })
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ticket_number", nullable = false, length = 30)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private AppUser reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private IncidentCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_version_id", nullable = false, updatable = false)
    private CategoryFormVersion formVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_ticket_id")
    private Ticket duplicateOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_skill_id")
    private Skill requiredSkill;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ticket_status", nullable = false, length = 30)
    private TicketStatus status = TicketStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resolution_outcome", length = 30)
    private ResolutionOutcome resolutionOutcome;

    @Column(name = "sla_due_at")
    private OffsetDateTime slaDueAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Ticket() {
    }

    /** Creates a submitted ticket without precomputed SLA or skill metadata. */
    public Ticket(
            String ticketNumber,
            AppUser reporter,
            IncidentCategory category,
            CategoryFormVersion formVersion,
            Location location,
            Asset asset,
            String title,
            String description,
            Priority priority) {
        this(
                ticketNumber,
                reporter,
                category,
                formVersion,
                location,
                asset,
                title,
                description,
                priority,
                null,
                null);
    }

    /**
     * Creates a submitted ticket with optional SLA and required-skill metadata.
     * Number generation and validation of category, asset, and SLA policies belong
     * to the later ticket-creation service.
     */
    public Ticket(
            String ticketNumber,
            AppUser reporter,
            IncidentCategory category,
            CategoryFormVersion formVersion,
            Location location,
            Asset asset,
            String title,
            String description,
            Priority priority,
            OffsetDateTime slaDueAt,
            Skill requiredSkill) {
        this.ticketNumber = Objects.requireNonNull(ticketNumber, "ticketNumber is required");
        this.reporter = Objects.requireNonNull(reporter, "reporter is required");
        this.category = Objects.requireNonNull(category, "category is required");
        this.formVersion = requireMatchingFormVersion(category, formVersion);
        this.location = Objects.requireNonNull(location, "location is required");
        this.asset = asset;
        this.title = Objects.requireNonNull(title, "title is required");
        this.description = Objects.requireNonNull(description, "description is required");
        this.priority = Objects.requireNonNull(priority, "priority is required");
        this.slaDueAt = slaDueAt;
        this.requiredSkill = requiredSkill;
    }

    @PrePersist
    void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static CategoryFormVersion requireMatchingFormVersion(
            IncidentCategory category,
            CategoryFormVersion formVersion) {
        CategoryFormVersion requiredForm = Objects.requireNonNull(
                formVersion, "formVersion is required");
        IncidentCategory formCategory = Objects.requireNonNull(
                requiredForm.getCategory(), "formVersion category is required");

        if (category == formCategory) {
            return requiredForm;
        }

        Long categoryId = category.getId();
        Long formCategoryId = formCategory.getId();
        if (categoryId == null || formCategoryId == null || !categoryId.equals(formCategoryId)) {
            throw new IllegalArgumentException("formVersion must belong to category");
        }
        return requiredForm;
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public AppUser getReporter() {
        return reporter;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public CategoryFormVersion getFormVersion() {
        return formVersion;
    }

    public Location getLocation() {
        return location;
    }

    public Asset getAsset() {
        return asset;
    }

    public Ticket getDuplicateOf() {
        return duplicateOf;
    }

    public Skill getRequiredSkill() {
        return requiredSkill;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public ResolutionOutcome getResolutionOutcome() {
        return resolutionOutcome;
    }

    public OffsetDateTime getSlaDueAt() {
        return slaDueAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void changeStatus(TicketStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
    }

    @Override
    public String toString() {
        return "Ticket{id=" + id + ", ticketNumber='" + ticketNumber + "', status=" + status + "}";
    }
}
