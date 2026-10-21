package com.nexora.ticket.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.ServiceAreaSummary;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.SkillSummary;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.TechnicianServiceAreaRepository;
import com.nexora.user.repository.TechnicianSkillRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@Transactional(readOnly = true)
public class TechnicianEligibilityService {

    private final AppUserRepository userRepository;
    private final TechnicianProfileRepository profileRepository;
    private final TechnicianSkillRepository skillRepository;
    private final TechnicianServiceAreaRepository serviceAreaRepository;
    private final TicketAssignmentRepository assignmentRepository;
    private final EntityManager entityManager;

    public TechnicianEligibilityService(
            AppUserRepository userRepository,
            TechnicianProfileRepository profileRepository,
            TechnicianSkillRepository skillRepository,
            TechnicianServiceAreaRepository serviceAreaRepository,
            TicketAssignmentRepository assignmentRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.skillRepository = skillRepository;
        this.serviceAreaRepository = serviceAreaRepository;
        this.assignmentRepository = assignmentRepository;
        this.entityManager = entityManager;
    }

    public PageResponse<EligibleTechnicianResponse> findEligibleTechnicians(
            Ticket ticket, EligibleTechniciansQuery query) {
        Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(query, "query is required");

        List<AppUser> candidateUsers = query.query() != null && !query.query().isEmpty()
                ? userRepository.findCandidateUsersWithRolesMatching(query.query())
                : userRepository.findAllCandidateUsersWithRoles();

        if (candidateUsers.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    query.pageable().getPageNumber(),
                    query.pageable().getPageSize(),
                    0,
                    0);
        }

        List<Long> candidateIds = candidateUsers.stream().map(AppUser::getId).toList();

        Map<Long, TechnicianProfile> profiles = profileRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(TechnicianProfile::getTechnicianId, Function.identity()));

        Map<Long, List<TechnicianSkill>> skillsByTech = skillRepository.findByTechnicianIdInWithSkill(candidateIds)
                .stream()
                .collect(Collectors.groupingBy(ts -> ts.getTechnician().getTechnicianId()));

        Map<Long, List<TechnicianServiceArea>> serviceAreasByTech = serviceAreaRepository
                .findByTechnicianIdInWithLocation(candidateIds).stream()
                .collect(Collectors.groupingBy(tsa -> tsa.getTechnician().getTechnicianId()));

        Map<Long, Integer> activeCounts = new HashMap<>();
        for (Object[] row : assignmentRepository.countActiveAssignmentsByTechnicianIds(candidateIds)) {
            Long techId = (Long) row[0];
            Long count = (Long) row[1];
            activeCounts.put(techId, count.intValue());
        }

        Set<Long> coveringLocationIds = getCoveringLocationIds(ticket.getLocation());

        List<EligibleTechnicianResponse> evaluated = new ArrayList<>();
        for (AppUser user : candidateUsers) {
            Long techId = user.getId();
            TechnicianProfile profile = profiles.get(techId);
            int activeCount = activeCounts.getOrDefault(techId, 0);
            List<TechnicianSkill> skills = skillsByTech.getOrDefault(techId, List.of());
            List<TechnicianServiceArea> serviceAreas = serviceAreasByTech.getOrDefault(techId, List.of());

            evaluated.add(buildCandidateResponse(
                    ticket, user, profile, activeCount, skills, serviceAreas, coveringLocationIds));
        }

        List<EligibleTechnicianResponse> filtered = filterByEligibility(evaluated, query.eligibility());

        long totalElements = filtered.size();
        int pageSize = query.pageable().getPageSize();
        int pageNumber = query.pageable().getPageNumber();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);

        int start = (int) query.pageable().getOffset();
        List<EligibleTechnicianResponse> pageContent;
        if (start >= totalElements) {
            pageContent = List.of();
        } else {
            int end = Math.min(start + pageSize, (int) totalElements);
            pageContent = filtered.subList(start, end);
        }

        return new PageResponse<>(pageContent, pageNumber, pageSize, totalElements, totalPages);
    }

    public EligibilityEvaluation evaluateCandidate(Ticket ticket, Long technicianId) {
        return evaluateCandidate(ticket, technicianId, false);
    }

    /** Caller holds Ticket then profile locks. Keep shared catalog inputs stable until commit too. */
    @Transactional(propagation = Propagation.MANDATORY)
    public EligibilityEvaluation evaluateCandidateForAssignment(Ticket ticket, Long technicianId) {
        return evaluateCandidate(ticket, technicianId, true);
    }

    private EligibilityEvaluation evaluateCandidate(Ticket ticket, Long technicianId, boolean forAssignment) {
        Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(technicianId, "technicianId is required");

        AppUser user = userRepository.findByIdWithRoles(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + technicianId));

        if (forAssignment) {
            // Also refresh when the actor is the candidate and AuthService loaded it before the profile lock.
            entityManager.refresh(user, LockModeType.PESSIMISTIC_READ);
            if (ticket.getRequiredSkill() != null)
                entityManager.refresh(ticket.getRequiredSkill(), LockModeType.PESSIMISTIC_READ);
            Location cursor = ticket.getLocation();
            while (cursor != null) {
                entityManager.refresh(cursor, LockModeType.PESSIMISTIC_READ);
                cursor = cursor.getParent();
            }
        }

        TechnicianProfile profile = profileRepository.findById(technicianId).orElse(null);
        long activeCount = assignmentRepository.countActiveAssignmentsByTechnicianId(technicianId);
        List<TechnicianSkill> skills = skillRepository.findByTechnicianIdWithSkill(technicianId);
        List<TechnicianServiceArea> serviceAreas = serviceAreaRepository.findByTechnicianIdWithLocation(technicianId);

        Set<Long> coveringLocationIds = getCoveringLocationIds(ticket.getLocation());

        List<EligibilityReason> reasons = evaluateReasons(
                ticket, user, profile, (int) activeCount, skills, serviceAreas, coveringLocationIds);

        return new EligibilityEvaluation(user, profile, reasons);
    }

    public Set<Long> getCoveringLocationIds(Location location) {
        Set<Long> ids = new HashSet<>();
        Location cursor = location;
        while (cursor != null) {
            ids.add(cursor.getId());
            cursor = cursor.getParent();
        }
        return ids;
    }

    public List<EligibilityReason> evaluateReasons(
            Ticket ticket,
            AppUser user,
            TechnicianProfile profile,
            int activeTicketCount,
            List<TechnicianSkill> skills,
            List<TechnicianServiceArea> serviceAreas,
            Set<Long> coveringLocationIds) {

        List<EligibilityReason> reasons = new ArrayList<>();

        if (user.getStatus() != UserStatus.ACTIVE) {
            reasons.add(new EligibilityReason("USER_INACTIVE", false));
        }

        boolean hasRole = user.getRoleAssignments().stream()
                .anyMatch(ra -> ra.getRole().getCode() == RoleCode.TECHNICIAN);
        if (!hasRole) {
            reasons.add(new EligibilityReason("MISSING_TECHNICIAN_ROLE", false));
        }

        if (profile == null) {
            reasons.add(new EligibilityReason("MISSING_TECHNICIAN_PROFILE", false));
            return reasons;
        }

        if (!profile.isAvailable()) {
            reasons.add(new EligibilityReason("UNAVAILABLE", false));
        }

        if (activeTicketCount >= profile.getMaxActiveTickets()) {
            reasons.add(new EligibilityReason("CAPACITY_REACHED", false));
        }

        if (ticket.getRequiredSkill() != null) {
            Long requiredSkillId = ticket.getRequiredSkill().getId();
            boolean hasMatchingActiveSkill = skills.stream()
                    .anyMatch(ts -> ts.getSkill().getId().equals(requiredSkillId) && ts.getSkill().isActive());
            if (!hasMatchingActiveSkill) {
                reasons.add(new EligibilityReason("SKILL_MISMATCH", false));
            }
        }

        boolean hasCoverage = serviceAreas.stream()
                .anyMatch(sa -> sa.getLocation().isActive() && coveringLocationIds.contains(sa.getLocation().getId()));
        if (!hasCoverage) {
            reasons.add(new EligibilityReason("COVERAGE_MISMATCH", false));
        }

        return reasons;
    }

    private EligibleTechnicianResponse buildCandidateResponse(
            Ticket ticket,
            AppUser user,
            TechnicianProfile profile,
            int activeTicketCount,
            List<TechnicianSkill> skills,
            List<TechnicianServiceArea> serviceAreas,
            Set<Long> coveringLocationIds) {

        boolean hasRole = user.getRoleAssignments().stream()
                .anyMatch(ra -> ra.getRole().getCode() == RoleCode.TECHNICIAN);
        boolean hasProfile = profile != null;
        Boolean available = hasProfile ? profile.isAvailable() : null;
        Integer maxActiveTickets = hasProfile ? profile.getMaxActiveTickets() : null;

        List<SkillSummary> skillSummaries = hasProfile
                ? skills.stream().map(ts -> new SkillSummary(ts.getSkill().getId(), ts.getSkill().getName())).toList()
                : List.of();

        List<ServiceAreaSummary> serviceAreaSummaries = hasProfile
                ? serviceAreas.stream()
                        .map(sa -> new ServiceAreaSummary(sa.getLocation().getId(), sa.getLocation().getName())).toList()
                : List.of();

        List<EligibilityReason> reasons = evaluateReasons(
                ticket, user, profile, activeTicketCount, skills, serviceAreas, coveringLocationIds);

        return new EligibleTechnicianResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getStatus(),
                hasRole,
                hasProfile,
                available,
                activeTicketCount,
                maxActiveTickets,
                skillSummaries,
                serviceAreaSummaries,
                reasons);
    }

    private List<EligibleTechnicianResponse> filterByEligibility(
            List<EligibleTechnicianResponse> candidates,
            EligibleTechniciansQuery.EligibilityFilter filter) {
        if (filter == null) {
            return candidates;
        }
        if (filter == EligibleTechniciansQuery.EligibilityFilter.ELIGIBLE) {
            return candidates.stream()
                    .filter(c -> c.eligibilityReasons().isEmpty())
                    .toList();
        }
        return candidates.stream()
                .filter(c -> !c.eligibilityReasons().isEmpty())
                .toList();
    }

    public record EligibilityEvaluation(
            AppUser user,
            TechnicianProfile profile,
            List<EligibilityReason> reasons) {
        public boolean isEligible() {
            return reasons.isEmpty();
        }
    }
}
