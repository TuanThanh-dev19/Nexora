package com.nexora.user.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "technician_profiles", schema = "dbo")
public class TechnicianProfile {

    public static final int DEFAULT_MAX_ACTIVE_TICKETS = 5;

    @Id
    @Column(name = "technician_id")
    private Long technicianId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private AppUser user;

    @Positive
    @Column(name = "max_active_tickets", nullable = false)
    private int maxActiveTickets = DEFAULT_MAX_ACTIVE_TICKETS;

    @Column(nullable = false)
    private boolean available = true;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "technician", fetch = FetchType.LAZY)
    private List<TechnicianSkill> skillAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "technician", fetch = FetchType.LAZY)
    private List<TechnicianServiceArea> serviceAreas = new ArrayList<>();

    protected TechnicianProfile() {
    }

    public TechnicianProfile(AppUser user) {
        this(user, DEFAULT_MAX_ACTIVE_TICKETS, true);
    }

    public TechnicianProfile(AppUser user, int maxActiveTickets, boolean available) {
        this.user = requirePersistedUser(user);
        this.technicianId = user.getId();
        this.maxActiveTickets = requirePositiveCapacity(maxActiveTickets);
        this.available = available;
    }

    public void changeAvailability(boolean available) {
        this.available = available;
    }

    public void changeMaxActiveTickets(int maxActiveTickets) {
        this.maxActiveTickets = requirePositiveCapacity(maxActiveTickets);
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

    private static AppUser requirePersistedUser(AppUser user) {
        AppUser requiredUser = Objects.requireNonNull(user, "user is required");
        if (requiredUser.getId() == null) {
            throw new IllegalArgumentException("user must be persisted before creating a technician profile");
        }
        return requiredUser;
    }

    private static int requirePositiveCapacity(int maxActiveTickets) {
        if (maxActiveTickets <= 0) {
            throw new IllegalArgumentException("maxActiveTickets must be greater than zero");
        }
        return maxActiveTickets;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public AppUser getUser() {
        return user;
    }

    public int getMaxActiveTickets() {
        return maxActiveTickets;
    }

    public boolean isAvailable() {
        return available;
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

    public List<TechnicianSkill> getSkillAssignments() {
        return Collections.unmodifiableList(skillAssignments);
    }

    public List<TechnicianServiceArea> getServiceAreas() {
        return Collections.unmodifiableList(serviceAreas);
    }
}
