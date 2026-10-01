package com.nexora.sla.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.nexora.category.entity.IssuePriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A service-level agreement policy that defines resolution time expectations
 * for a given priority level.
 *
 * <p>Maps to {@code dbo.sla_policies} (created in V6 migration).
 *
 * <p>This entity is reference data. Mutations are limited to toggling
 * {@link #active} via {@link #changeActive(boolean)}. All other fields
 * are set at construction time and are immutable thereafter.
 *
 * <p>Cross-module references: {@code category_form_versions.default_sla_policy_id}
 * stores this entity's {@link #id} as a narrow {@code Long} reference.
 */
@Entity
@Table(name = "sla_policies", schema = "dbo")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable name for the policy (e.g. "Standard — Normal").
     * Unique across all policies (enforced by {@code uq_sla_policies_name}).
     */
    @Nationalized
    @Column(name = "policy_name", nullable = false, length = 150, unique = true)
    private String policyName;

    /**
     * The priority level this policy governs.
     * Validated by {@code ck_sla_policies_priority}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private IssuePriority priority;

    /**
     * Maximum number of minutes to resolve a ticket under this policy.
     * Must be {@code > 0} (enforced by {@code ck_sla_policies_resolution_minutes}).
     */
    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    /**
     * When {@code true}, the SLA clock is paused while the ticket is waiting
     * for a response from the requester.
     */
    @Column(name = "pause_waiting_requester", nullable = false)
    private boolean pauseWaitingRequester = false;

    /** Whether this policy is available for use on new form versions. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA — not for direct use. */
    protected SlaPolicy() {
    }

    /**
     * Creates a new SLA policy.
     *
     * @param policyName        unique display name; must not be {@code null}
     * @param priority          the priority level this policy covers; must not be {@code null}
     * @param resolutionMinutes maximum minutes to resolve; must be {@code > 0}
     */
    public SlaPolicy(String policyName, IssuePriority priority, int resolutionMinutes) {
        this.policyName = Objects.requireNonNull(policyName, "policyName is required");
        this.priority = Objects.requireNonNull(priority, "priority is required");
        if (resolutionMinutes <= 0) {
            throw new IllegalArgumentException("resolutionMinutes must be > 0");
        }
        this.resolutionMinutes = resolutionMinutes;
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /** Activates or deactivates this policy. */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /** Sets whether the SLA clock pauses when waiting on the requester. */
    public void changePauseWaitingRequester(boolean pauseWaitingRequester) {
        this.pauseWaitingRequester = pauseWaitingRequester;
    }

    // -------------------------------------------------------------------------
    // JPA lifecycle
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Accessors — read-only
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public IssuePriority getPriority() {
        return priority;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public boolean isPauseWaitingRequester() {
        return pauseWaitingRequester;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "SlaPolicy{id=" + id + ", policyName='" + policyName + "', priority=" + priority
                + ", resolutionMinutes=" + resolutionMinutes + "}";
    }
}
