package com.nexora.dashboard.application;

import java.util.Set;

import com.nexora.user.entity.RoleCode;

/** Current-user context used by the future role-scoped dashboard query. */
public record DashboardViewer(long userId, Set<RoleCode> roles) {

    public DashboardViewer {
        roles = Set.copyOf(roles);
    }
}
