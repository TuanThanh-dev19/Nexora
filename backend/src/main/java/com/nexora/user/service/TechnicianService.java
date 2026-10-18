package com.nexora.user.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.dto.TechnicianListQuery;
import com.nexora.user.dto.TechnicianProfileResponse;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.TechnicianSkillsUpdateRequest;
import com.nexora.user.dto.TechnicianSummaryResponse;
import com.nexora.user.dto.TechnicianUpdateRequest;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.SkillRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.TechnicianServiceAreaRepository;
import com.nexora.user.repository.TechnicianSkillRepository;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
@Transactional
public class TechnicianService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianService.class);

    private final TechnicianProfileRepository technicianProfileRepository;
    private final TechnicianSkillRepository technicianSkillRepository;
    private final TechnicianServiceAreaRepository technicianServiceAreaRepository;
    private final AppUserRepository userRepository;
    private final SkillRepository skillRepository;
    private final LocationRepository locationRepository;
    private final AuthService authService;

    @org.springframework.beans.factory.annotation.Autowired
    public TechnicianService(
            TechnicianProfileRepository technicianProfileRepository,
            TechnicianSkillRepository technicianSkillRepository,
            TechnicianServiceAreaRepository technicianServiceAreaRepository,
            AppUserRepository userRepository,
            SkillRepository skillRepository,
            LocationRepository locationRepository,
            AuthService authService) {
        this.technicianProfileRepository = technicianProfileRepository;
        this.technicianSkillRepository = technicianSkillRepository;
        this.technicianServiceAreaRepository = technicianServiceAreaRepository;
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.locationRepository = locationRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TechnicianSummaryResponse> listTechnicians(Jwt jwt, TechnicianListQuery query) {
        CurrentUserResponse viewer = getViewer(jwt);
        requireManagerOrAdmin(viewer);

        Page<TechnicianProfile> page = technicianProfileRepository.findAll(
                query.toSpecification(), query.pageable());
        return PageResponse.from(page.map(TechnicianSummaryResponse::from));
    }

    private CurrentUserResponse getViewer(Jwt jwt) {
        if (jwt == null) {
            throw new BadCredentialsException("Authentication required.");
        }
        if (authService == null) {
            throw new IllegalStateException("AuthService is not configured");
        }
        return authService.getCurrentUser(jwt);
    }

    private void requireManagerOrAdmin(CurrentUserResponse viewer) {
        boolean isManagerOrAdmin = viewer.roles() != null && (
                viewer.roles().contains(RoleCode.MANAGER.name()) ||
                viewer.roles().contains(RoleCode.ADMIN.name())
        );
        if (!isManagerOrAdmin) {
            throw new AccessDeniedException("Only managers and admins can list technicians.");
        }
    }

    @Transactional(readOnly = true)
    public TechnicianProfileResponse getProfile(Long technicianId, Long currentUserId, boolean isManagerOrAdmin) {
        if (!isManagerOrAdmin && !Objects.equals(technicianId, currentUserId)) {
            log.warn("Access denied: technician userId={} attempted to access technicianId={}",
                    currentUserId, technicianId);
            throw new AccessDeniedException("Technicians can only access their own profile");
        }

        TechnicianProfile profile = technicianProfileRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found for id: " + technicianId));

        List<TechnicianSkill> skills = technicianSkillRepository.findByTechnicianIdWithSkill(technicianId);
        List<TechnicianServiceArea> serviceAreas = technicianServiceAreaRepository
                .findByTechnicianIdWithLocation(technicianId);

        return TechnicianProfileResponse.from(profile, skills, serviceAreas);
    }

    public TechnicianProfileResponse updateProfile(Long technicianId, TechnicianUpdateRequest request) {
        AppUser user = userRepository.findByIdWithRoles(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + technicianId));

        boolean hasTechRole = user.getRoleAssignments().stream()
                .anyMatch(ra -> ra.getRole().getCode() == RoleCode.TECHNICIAN);
        if (!hasTechRole) {
            log.warn("Cannot configure technician profile: userId={} is not a TECHNICIAN", technicianId);
            throw new BusinessRuleException("User " + technicianId + " does not have the TECHNICIAN role");
        }

        TechnicianProfile profile = technicianProfileRepository.findById(technicianId).orElse(null);
        if (profile != null) {
            if (request.version() != null && !Objects.equals(profile.getVersion(), request.version())) {
                log.warn("Optimistic locking conflict on technician profile id={}: expected={} current={}",
                        technicianId, request.version(), profile.getVersion());
                throw new StaleWriteException();
            }
            profile.changeAvailability(request.available());
            profile.changeMaxActiveTickets(request.maxActiveTickets());
        } else {
            profile = new TechnicianProfile(user, request.maxActiveTickets(), request.available());
        }

        profile = technicianProfileRepository.save(profile);
        log.info("Updated technician profile id={} available={} maxActiveTickets={}",
                technicianId, request.available(), request.maxActiveTickets());

        List<TechnicianSkill> skills = technicianSkillRepository.findByTechnicianIdWithSkill(technicianId);
        List<TechnicianServiceArea> serviceAreas = technicianServiceAreaRepository
                .findByTechnicianIdWithLocation(technicianId);

        return TechnicianProfileResponse.from(profile, skills, serviceAreas);
    }

    public TechnicianProfileResponse updateSkills(Long technicianId, TechnicianSkillsUpdateRequest request) {
        TechnicianProfile profile = technicianProfileRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found for id: " + technicianId));

        if (request.version() != null && !Objects.equals(profile.getVersion(), request.version())) {
            log.warn("Optimistic locking conflict on technician profile id={}: expected={} current={}",
                    technicianId, request.version(), profile.getVersion());
            throw new StaleWriteException();
        }

        Set<Long> uniqueSkillIds = new HashSet<>();
        for (var item : request.skills()) {
            if (!uniqueSkillIds.add(item.skillId())) {
                throw new BusinessRuleException("Duplicate skill in request: " + item.skillId());
            }
        }

        List<Skill> skills = skillRepository.findAllById(uniqueSkillIds);
        if (skills.size() != uniqueSkillIds.size()) {
            Set<Long> foundIds = skills.stream().map(Skill::getId).collect(Collectors.toSet());
            List<Long> missingIds = uniqueSkillIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ResourceNotFoundException("Skills not found: " + missingIds);
        }

        Map<Long, Skill> skillMap = skills.stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        technicianSkillRepository.deleteByTechnicianId(technicianId);

        List<TechnicianSkill> newAssignments = request.skills().stream()
                .map(item -> new TechnicianSkill(profile, skillMap.get(item.skillId()), item.proficiencyLevel()))
                .toList();
        technicianSkillRepository.saveAll(newAssignments);
        log.info("Updated skills for technician id={}, assigned count={}", technicianId, newAssignments.size());

        List<TechnicianSkill> updatedSkills = technicianSkillRepository.findByTechnicianIdWithSkill(technicianId);
        List<TechnicianServiceArea> serviceAreas = technicianServiceAreaRepository
                .findByTechnicianIdWithLocation(technicianId);

        return TechnicianProfileResponse.from(profile, updatedSkills, serviceAreas);
    }

    public TechnicianProfileResponse updateServiceAreas(
            Long technicianId, TechnicianServiceAreasUpdateRequest request) {
        TechnicianProfile profile = technicianProfileRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician profile not found for id: " + technicianId));

        if (request.version() != null && !Objects.equals(profile.getVersion(), request.version())) {
            log.warn("Optimistic locking conflict on technician profile id={}: expected={} current={}",
                    technicianId, request.version(), profile.getVersion());
            throw new StaleWriteException();
        }

        Set<Long> uniqueLocationIds = new HashSet<>(request.locationIds());
        List<Location> locations = locationRepository.findAllById(uniqueLocationIds);
        if (locations.size() != uniqueLocationIds.size()) {
            Set<Long> foundIds = locations.stream().map(Location::getId).collect(Collectors.toSet());
            List<Long> missingIds = uniqueLocationIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ResourceNotFoundException("Locations not found: " + missingIds);
        }

        technicianServiceAreaRepository.deleteByTechnicianId(technicianId);

        List<TechnicianServiceArea> newAreas = locations.stream()
                .map(loc -> new TechnicianServiceArea(profile, loc))
                .toList();
        technicianServiceAreaRepository.saveAll(newAreas);
        log.info("Updated service areas for technician id={}, assigned count={}", technicianId, newAreas.size());

        List<TechnicianSkill> skills = technicianSkillRepository.findByTechnicianIdWithSkill(technicianId);
        List<TechnicianServiceArea> updatedAreas = technicianServiceAreaRepository
                .findByTechnicianIdWithLocation(technicianId);

        return TechnicianProfileResponse.from(profile, skills, updatedAreas);
    }
}
