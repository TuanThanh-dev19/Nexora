package com.nexora.location.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "locations", schema = "dbo")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "location_type", nullable = false, length = 20)
    private LocationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Location parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("name ASC, code ASC, id ASC")
    private List<Location> children = new ArrayList<>();

    @Nationalized
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Location() {
    }

    public Location(String code, String name, LocationType type, Location parent) {
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.parent = parent;
    }

    public void changeName(String name) {
        this.name = Objects.requireNonNull(name, "name is required");
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    public void changeParent(Location parent) {
        this.parent = parent;
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

    public LocationType getType() {
        return type;
    }

    @JsonIgnore
    public Location getParent() {
        return parent;
    }

    @JsonIgnore
    public List<Location> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public String getDescription() {
        return description;
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
        return "Location{id=" + id + ", code='" + code + "'}";
    }
}
