package com.nexora.asset.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.Nationalized;

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
 * An immutable audit record of a single asset status transition.
 *
 * <p>Instances are created only by a dedicated transactional service that
 * performs the transition.  No setters are provided — the record must not
 * be modified after it has been persisted.
 *
 * <p>The optional {@code ticketId} stores the raw FK to {@code dbo.tickets}
 * without an entity association, because the {@code Ticket} entity is not
 * yet available in this milestone.  The association will be promoted to a
 * {@code @ManyToOne} once the ticket persistence layer is in place.
 */
@Entity
@Table(name = "asset_status_history", schema = "dbo")
public class AssetStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The asset whose status changed. Never {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /**
     * The status the asset held <em>before</em> this transition.
     * {@code null} when the asset is being registered for the first time.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private AssetStatus fromStatus;

    /** The status the asset transitioned <em>to</em>. Never {@code null}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private AssetStatus toStatus;

    /** Human-readable explanation of why the status changed. Never {@code null}. */
    @Nationalized
    @Column(nullable = false, length = 500)
    private String reason;

    /**
     * The user who performed the transition, or {@code null} if performed
     * by an automated process or a migration.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private AppUser changedBy;

    /**
     * Raw FK to {@code dbo.tickets}.  Nullable — most transitions are not
     * related to a ticket.  Will be promoted to a {@code @ManyToOne} once
     * the Ticket persistence layer is introduced.
     */
    @Column(name = "ticket_id")
    private Long ticketId;

    /** Timestamp at which the transition was recorded. Set on first persist. */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    /** Required by JPA — not for direct use. */
    protected AssetStatusHistory() {
    }

    /**
     * Records a status transition for an asset.
     *
     * @param asset      the asset that changed state; must not be {@code null}
     * @param fromStatus the previous status; may be {@code null} on initial registration
     * @param toStatus   the new status; must not be {@code null}
     * @param reason     mandatory explanation; must not be {@code null}
     * @param changedBy  the acting user; may be {@code null} for automated changes
     */
    public AssetStatusHistory(
            Asset asset,
            AssetStatus fromStatus,
            AssetStatus toStatus,
            String reason,
            AppUser changedBy) {
        this.asset = Objects.requireNonNull(asset, "asset is required");
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus is required");
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.fromStatus = fromStatus;   // nullable — first registration has no prior status
        this.changedBy = changedBy;     // nullable — may be automated
    }

    /**
     * Convenience constructor that also captures an optional related ticket.
     *
     * @param asset      the asset that changed state; must not be {@code null}
     * @param fromStatus the previous status; may be {@code null}
     * @param toStatus   the new status; must not be {@code null}
     * @param reason     mandatory explanation; must not be {@code null}
     * @param changedBy  the acting user; may be {@code null}
     * @param ticketId   optional FK to the related ticket; may be {@code null}
     */
    public AssetStatusHistory(
            Asset asset,
            AssetStatus fromStatus,
            AssetStatus toStatus,
            String reason,
            AppUser changedBy,
            Long ticketId) {
        this(asset, fromStatus, toStatus, reason, changedBy);
        this.ticketId = ticketId;
    }

    // -------------------------------------------------------------------------
    // JPA lifecycle
    // -------------------------------------------------------------------------

    @PrePersist
    void initializeChangedAt() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors — immutable after persist; no setters provided
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public AssetStatus getFromStatus() {
        return fromStatus;
    }

    public AssetStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public AppUser getChangedBy() {
        return changedBy;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return "AssetStatusHistory{id=" + id
                + ", fromStatus=" + fromStatus
                + ", toStatus=" + toStatus
                + ", changedAt=" + changedAt + "}";
    }
}
