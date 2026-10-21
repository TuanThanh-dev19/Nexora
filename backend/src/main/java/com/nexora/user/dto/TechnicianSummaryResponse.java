package com.nexora.user.dto;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary of a technician profile for workforce listing")
public record TechnicianSummaryResponse(
        @Schema(description = "Associated user ID and technician profile key", example = "21")
        Long technicianId,

        @Schema(description = "Technician full name", example = "Nguyễn An")
        String fullName,

        @Schema(description = "Technician email address", example = "an@example.test")
        String email,

        @Schema(description = "Account status (ACTIVE, LOCKED, or DISABLED)", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "Whether the technician is available to take work", example = "true")
        boolean available,

        @Schema(description = "Maximum active tickets capacity", example = "5")
        int maxActiveTickets,

        @Schema(description = "Optimistic locking version", example = "2")
        Long version
) {
    public static TechnicianSummaryResponse from(TechnicianProfile profile) {
        AppUser user = profile.getUser();
        return new TechnicianSummaryResponse(
                profile.getTechnicianId(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getStatus() : null,
                profile.isAvailable(),
                profile.getMaxActiveTickets(),
                profile.getVersion()
        );
    }
}
