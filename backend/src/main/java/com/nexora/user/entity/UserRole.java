package com.nexora.user.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_roles", schema = "dbo")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AppUser assignedBy;

    protected UserRole() {
    }

    public UserRole(AppUser user, Role role, AppUser assignedBy) {
        this(user, role, assignedBy, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public UserRole(AppUser user, Role role, AppUser assignedBy, OffsetDateTime assignedAt) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.role = Objects.requireNonNull(role, "role is required");
        this.assignedBy = assignedBy;
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt is required");
        this.id = new UserRoleId(
                Objects.requireNonNull(user.getId(), "user must be persisted before role assignment"),
                Objects.requireNonNull(role.getId(), "role must be persisted before assignment"));
    }

    @PrePersist
    void initializeAssignedAt() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UserRoleId getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }
}
