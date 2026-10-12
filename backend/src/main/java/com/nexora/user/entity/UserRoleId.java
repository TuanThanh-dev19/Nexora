package com.nexora.user.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Short roleId;

    protected UserRoleId() {
    }

    public UserRoleId(Long userId, Short roleId) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.roleId = Objects.requireNonNull(roleId, "roleId is required");
    }

    public Long getUserId() {
        return userId;
    }

    public Short getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
