package com.nexora.user.dto;

import java.util.Set;

import com.nexora.user.entity.RoleCode;

import jakarta.validation.constraints.NotEmpty;

public record UserRolesUpdateRequest(
        @NotEmpty(message = "roles must not be empty")
        Set<RoleCode> roles
) {
}
