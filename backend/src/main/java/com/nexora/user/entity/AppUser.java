package com.nexora.user.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_users", schema = "dbo")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "normalized_email", length = 254, insertable = false, updatable = false)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<UserRole> roleAssignments = new ArrayList<>();

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String fullName) {
        this(email, passwordHash, fullName, UserStatus.ACTIVE);
    }

    public AppUser(String email, String passwordHash, String fullName, UserStatus status) {
        this.email = Objects.requireNonNull(email, "email is required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required");
        this.fullName = Objects.requireNonNull(fullName, "fullName is required");
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public void changeEmail(String email) {
        this.email = Objects.requireNonNull(email, "email is required");
    }

    public void changeFullName(String fullName) {
        this.fullName = Objects.requireNonNull(fullName, "fullName is required");
    }

    public void changeStatus(UserStatus status) {
        this.status = Objects.requireNonNull(status, "status is required");
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

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public UserStatus getStatus() {
        return status;
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

    public List<UserRole> getRoleAssignments() {
        return Collections.unmodifiableList(roleAssignments);
    }
}
