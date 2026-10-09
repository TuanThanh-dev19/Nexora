package com.nexora.user.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        UserStatus status,
        List<RoleCode> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
    public static UserResponse from(AppUser user) {
        List<RoleCode> roleCodes = user.getRoleAssignments() != null
                ? user.getRoleAssignments().stream()
                        .map(ra -> ra.getRole().getCode())
                        .sorted()
                        .toList()
                : List.of();

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleCodes,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion()
        );
    }

    public static UserResponse from(AppUser user, List<RoleCode> roleCodes) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleCodes != null ? roleCodes.stream().sorted().toList() : List.of(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getVersion()
        );
    }
}
