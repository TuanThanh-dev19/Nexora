package com.nexora.asset.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_types", schema = "dbo")
public class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Nationalized
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Nationalized
    @Column(length = 100)
    private String brand;

    @Nationalized
    @Column(length = 100)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EquipmentType() {
    }

    public EquipmentType(String code, String name) {
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
    }

    public void changeName(String name) {
        this.name = Objects.requireNonNull(name, "name is required");
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeBrandAndModel(String brand, String model) {
        this.brand = brand;
        this.model = model;
    }

    public void changeActive(boolean active) {
        this.active = active;
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

    public boolean isActive() {
        return active;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
