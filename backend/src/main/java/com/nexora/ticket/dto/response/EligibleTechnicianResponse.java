package com.nexora.ticket.dto.response;

import java.util.List;

import com.nexora.user.entity.UserStatus;

public record EligibleTechnicianResponse(
        Long technicianId,
        String fullName,
        String email,
        UserStatus userStatus,
        boolean hasTechnicianRole,
        boolean hasProfile,
        Boolean available,
        int activeTicketCount,
        Integer maxActiveTickets,
        List<SkillSummary> skills,
        List<ServiceAreaSummary> serviceAreas,
        List<EligibilityReason> eligibilityReasons) {

    public EligibleTechnicianResponse {
        skills = skills == null ? List.of() : List.copyOf(skills);
        serviceAreas = serviceAreas == null ? List.of() : List.copyOf(serviceAreas);
        eligibilityReasons = eligibilityReasons == null ? List.of() : List.copyOf(eligibilityReasons);
    }

    public record SkillSummary(Long id, String name) {
    }

    public record ServiceAreaSummary(Long id, String name) {
    }

    public record EligibilityReason(String code, boolean overridable) {
        public EligibilityReason(String code) {
            this(code, false);
        }
    }
}
