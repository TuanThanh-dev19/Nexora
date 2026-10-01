package com.nexora.asset.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.nexora.location.entity.Location;

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
import jakarta.persistence.Version;

/**
 * A physical campus asset (piece of equipment) tracked in the system.
 *
 * <p>Status transitions are intentionally excluded from this entity.
 * Use a dedicated transactional service to change {@link #status} and
 * record the corresponding {@link AssetStatusHistory} entry.
 */
@Entity
@Table(name = "assets", schema = "dbo")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique human-readable identifier for the asset (e.g. "PROJ-A101-001"). */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asset_code", nullable = false, length = 80, unique = true)
    private String assetCode;

    /** Optional manufacturer serial number. Unique when present (filtered unique index). */
    @Nationalized
    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    /** Classification of this asset. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_type_id", nullable = false)
    private EquipmentType equipmentType;

    /** Physical location where this asset resides. Never null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /**
     * Current lifecycle state.
     * Default value is {@link AssetStatus#ACTIVE}.
     * Must only be changed through a dedicated transactional service.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asset_status", nullable = false, length = 30)
    private AssetStatus status = AssetStatus.ACTIVE;

    @Nationalized
    @Column(length = 500)
    private String description;

    /** Date on which the asset was purchased. Nullable. */
    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    /** Date until which the manufacturer warranty is valid. Nullable. Added in V6. */
    @Column(name = "warranty_until")
    private LocalDate warrantyUntil;

    /**
     * Optimistic-lock version. Incremented by the persistence provider on
     * every update — do not set this field manually.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Required by JPA — not for direct use. */
    protected Asset() {
    }

    /**
     * Creates a new asset with the minimum required attributes.
     * Status defaults to {@link AssetStatus#ACTIVE}.
     *
     * @param assetCode     unique business identifier; must not be {@code null}
     * @param equipmentType classification; must not be {@code null}
     * @param location      physical location; must not be {@code null}
     */
    public Asset(String assetCode, EquipmentType equipmentType, Location location) {
        this.assetCode = Objects.requireNonNull(assetCode, "assetCode is required");
        this.equipmentType = Objects.requireNonNull(equipmentType, "equipmentType is required");
        this.location = Objects.requireNonNull(location, "location is required");
    }

    // -------------------------------------------------------------------------
    // Mutation methods — no status change here (belongs to a service)
    // -------------------------------------------------------------------------

    /** Updates the free-text description. {@code null} is accepted (clears the field). */
    public void changeDescription(String description) {
        this.description = description;
    }

    /** Relocates the asset to a different location. Must not be {@code null}. */
    public void changeLocation(Location location) {
        this.location = Objects.requireNonNull(location, "location is required");
    }

    /** Sets or clears the optional manufacturer serial number. */
    public void changeSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    /** Sets or clears the purchase date. */
    public void changePurchasedAt(LocalDate purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    /** Sets or clears the warranty expiry date. */
    public void changeWarrantyUntil(LocalDate warrantyUntil) {
        this.warrantyUntil = warrantyUntil;
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

    public String getAssetCode() {
        return assetCode;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public EquipmentType getEquipmentType() {
        return equipmentType;
    }

    public Location getLocation() {
        return location;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getPurchasedAt() {
        return purchasedAt;
    }

    public LocalDate getWarrantyUntil() {
        return warrantyUntil;
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

    @Override
    public String toString() {
        return "Asset{id=" + id + ", assetCode='" + assetCode + "', status=" + status + "}";
    }
}
