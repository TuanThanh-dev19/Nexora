package com.nexora.category.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

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
 * A classification for campus incidents (e.g. "Projector Fault", "Network Issue").
 *
 * <p>Maps to {@code dbo.incident_categories} (created in V3, extended in V6).
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>{@link #createdById} is a <em>narrow</em> reference — only the user PK is
 *       stored, not the full {@code AppUser} association. This keeps the entity
 *       graph small and avoids a cross-module JPA dependency.</li>
 *   <li>No collection of {@link CategoryFormVersion} is held here. Use
 *       {@code CategoryFormVersionRepository} to navigate from category to its
 *       form versions.</li>
 *   <li>Active/inactive toggling belongs to an administrative service, not here.</li>
 * </ul>
 */
@Entity
@Table(name = "incident_categories", schema = "dbo")
public class IncidentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique machine-readable code for this category (e.g. "PROJECTOR_FAULT").
     * Enforced unique by {@code uq_incident_categories_code}.
     */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    /** Display name shown to reporters. */
    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    /** Optional explanation of when to use this category. */
    @Nationalized
    @Column(length = 1000)
    private String description;

    /**
     * Whether reporters must link, may link, or must not link an asset.
     * Validated by {@code ck_incident_categories_asset_policy}.
     * Default: {@link AssetPolicy#OPTIONAL}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asset_policy", nullable = false, length = 20)
    private AssetPolicy assetPolicy = AssetPolicy.OPTIONAL;

    /**
     * Whether the reporter must specify a campus location when submitting.
     * Default: {@code true}.
     */
    @Column(name = "location_required", nullable = false)
    private boolean locationRequired = true;

    /**
     * Default priority assigned to new tickets in this category.
     * Validated by {@code ck_incident_categories_priority}.
     * Default: {@link IssuePriority#NORMAL}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "default_priority", nullable = false, length = 20)
    private IssuePriority defaultPriority = IssuePriority.NORMAL;

    /**
     * Legacy SLA hours field. Nullable; kept for backward compatibility.
     * Superseded by {@code default_sla_policy_id} on form versions for new tickets.
     */
    @Column(name = "sla_hours")
    private Integer slaHours;

    /** Whether this category is available for new ticket submissions. Default: {@code true}. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * The user who created this category.
     * Stored as a narrow ID reference — see class Javadoc for rationale.
     * Added in V6 migration. Nullable.
     */
    @Column(name = "created_by")
    private Long createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA — not for direct use. */
    protected IncidentCategory() {
    }

    /**
     * Creates a new incident category with the minimum required fields.
     *
     * @param code unique machine-readable identifier; must not be {@code null}
     * @param name display name; must not be {@code null}
     */
    public IncidentCategory(String code, String name) {
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /** Updates the display name. Must not be {@code null}. */
    public void changeName(String name) {
        this.name = Objects.requireNonNull(name, "name is required");
    }

    /** Sets or clears the optional description. */
    public void changeDescription(String description) {
        this.description = description;
    }

    /** Updates the asset-linking policy. Must not be {@code null}. */
    public void changeAssetPolicy(AssetPolicy assetPolicy) {
        this.assetPolicy = Objects.requireNonNull(assetPolicy, "assetPolicy is required");
    }

    /** Sets whether reporters must provide a location. */
    public void changeLocationRequired(boolean locationRequired) {
        this.locationRequired = locationRequired;
    }

    /** Updates the default priority for new tickets. Must not be {@code null}. */
    public void changeDefaultPriority(IssuePriority defaultPriority) {
        this.defaultPriority = Objects.requireNonNull(defaultPriority, "defaultPriority is required");
    }

    /** Sets or clears the legacy SLA hours field. */
    public void changeSlaHours(Integer slaHours) {
        this.slaHours = slaHours;
    }

    /** Activates or deactivates this category. */
    public void changeActive(boolean active) {
        this.active = active;
    }

    /** Sets the narrow creator reference. */
    public void changeCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    // -------------------------------------------------------------------------
    // JPA lifecycle — timestamps only; no business validation
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AssetPolicy getAssetPolicy() {
        return assetPolicy;
    }

    public boolean isLocationRequired() {
        return locationRequired;
    }

    public IssuePriority getDefaultPriority() {
        return defaultPriority;
    }

    public Integer getSlaHours() {
        return slaHours;
    }

    public boolean isActive() {
        return active;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "IncidentCategory{id=" + id + ", code='" + code + "', active=" + active + "}";
    }
}
