package com.nexora.ticket.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.service.TechnicianEligibilityService.EligibilityEvaluation;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.TechnicianServiceAreaRepository;
import com.nexora.user.repository.TechnicianSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TechnicianEligibilityServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private TechnicianProfileRepository profileRepository;

    @Mock
    private TechnicianSkillRepository skillRepository;

    @Mock
    private TechnicianServiceAreaRepository serviceAreaRepository;

    @Mock
    private TicketAssignmentRepository assignmentRepository;

    private TechnicianEligibilityService service;

    private Location campus;
    private Location buildingA;
    private Location floor1;
    private Location room101;
    private Location room102;

    private Skill electricalSkill;
    private Skill plumbingSkill;

    private Ticket ticket;
    private AppUser reporter;

    @BeforeEach
    void setUp() {
        service = new TechnicianEligibilityService(
                userRepository,
                profileRepository,
                skillRepository,
                serviceAreaRepository,
                assignmentRepository, org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class));

        // Location hierarchy: Campus -> Building A -> Floor 1 -> Room 101, Room 102
        campus = new Location("CAMPUS-1", "Main Campus", LocationType.CAMPUS, null);
        ReflectionTestUtils.setField(campus, "id", 1L);

        buildingA = new Location("BLD-A", "Building A", LocationType.BUILDING, campus);
        ReflectionTestUtils.setField(buildingA, "id", 2L);

        floor1 = new Location("FL-1", "Floor 1", LocationType.FLOOR, buildingA);
        ReflectionTestUtils.setField(floor1, "id", 3L);

        room101 = new Location("RM-101", "Room 101", LocationType.ROOM, floor1);
        ReflectionTestUtils.setField(room101, "id", 4L);

        room102 = new Location("RM-102", "Room 102", LocationType.ROOM, floor1);
        ReflectionTestUtils.setField(room102, "id", 5L);

        electricalSkill = new Skill("ELEC", "Electrical");
        ReflectionTestUtils.setField(electricalSkill, "id", 10L);

        plumbingSkill = new Skill("PLUMB", "Plumbing");
        ReflectionTestUtils.setField(plumbingSkill, "id", 11L);

        reporter = new AppUser("reporter@nexora.local", "hash", "Reporter User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(reporter, "id", 100L);

        IncidentCategory category = new IncidentCategory("MAINT", "General Maintenance");
        ReflectionTestUtils.setField(category, "id", 50L);
        CategoryFormVersion formVersion = new CategoryFormVersion(category, 1);
        ReflectionTestUtils.setField(formVersion, "id", 100L);

        ticket = new Ticket(
                "TCK-2026-0001",
                reporter,
                category,
                formVersion,
                room101,
                null,
                "Broken AC",
                "AC is leaking water",
                Priority.HIGH,
                null,
                electricalSkill);
        ReflectionTestUtils.setField(ticket, "id", 500L);
    }

    private AppUser createUser(Long id, String name, UserStatus status, boolean hasTechRole) {
        AppUser user = new AppUser("user" + id + "@nexora.local", "hash", name, status);
        ReflectionTestUtils.setField(user, "id", id);
        if (hasTechRole) {
            Role role = new Role(RoleCode.TECHNICIAN, "Technician");
            ReflectionTestUtils.setField(role, "id", (short) 2);
            UserRole ur = new UserRole(user, role, null);
            ReflectionTestUtils.setField(user, "roleAssignments", List.of(ur));
        } else {
            Role role = new Role(RoleCode.REQUESTER, "Standard User");
            ReflectionTestUtils.setField(role, "id", (short) 1);
            UserRole ur = new UserRole(user, role, null);
            ReflectionTestUtils.setField(user, "roleAssignments", List.of(ur));
        }
        return user;
    }

    private TechnicianProfile createProfile(AppUser user, boolean available, int maxCapacity) {
        TechnicianProfile profile = new TechnicianProfile(user);
        ReflectionTestUtils.setField(profile, "available", available);
        ReflectionTestUtils.setField(profile, "maxActiveTickets", maxCapacity);
        return profile;
    }

    // ==========================================
    // 10.1 Eligibility Service Individual Rules
    // ==========================================

    @Test
    @DisplayName("evaluateCandidate: USER_INACTIVE khi trạng thái người dùng không phải ACTIVE")
    void evaluate_userInactive_returnsReason() {
        AppUser user = createUser(1L, "Inactive Tech", UserStatus.LOCKED, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(1L)).willReturn(Optional.of(user));
        given(profileRepository.findById(1L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(1L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(1L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(1L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 1L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).hasSize(1);
        EligibilityReason reason = result.reasons().get(0);
        assertThat(reason.code()).isEqualTo("USER_INACTIVE");
        assertThat(reason.overridable()).isFalse();
    }

    @Test
    @DisplayName("evaluateCandidate: MISSING_TECHNICIAN_ROLE khi người dùng không có role TECHNICIAN")
    void evaluate_missingTechnicianRole_returnsReason() {
        AppUser user = createUser(2L, "Non Tech User", UserStatus.ACTIVE, false);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(2L)).willReturn(Optional.of(user));
        given(profileRepository.findById(2L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(2L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(2L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(2L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 2L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("MISSING_TECHNICIAN_ROLE");
    }

    @Test
    @DisplayName("evaluateCandidate: MISSING_TECHNICIAN_PROFILE khi không có profile; không sinh lý do ảo về capacity, skill hay coverage")
    void evaluate_missingTechnicianProfile_noFalseReasons() {
        AppUser user = createUser(3L, "No Profile Tech", UserStatus.ACTIVE, true);

        given(userRepository.findByIdWithRoles(3L)).willReturn(Optional.of(user));
        given(profileRepository.findById(3L)).willReturn(Optional.empty());
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(3L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(3L)).willReturn(List.of());
        given(serviceAreaRepository.findByTechnicianIdWithLocation(3L)).willReturn(List.of());

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 3L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("MISSING_TECHNICIAN_PROFILE");
    }

    @Test
    @DisplayName("evaluateCandidate: UNAVAILABLE khi profile.available = false")
    void evaluate_unavailable_returnsReason() {
        AppUser user = createUser(4L, "Busy Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, false, 5);

        given(userRepository.findByIdWithRoles(4L)).willReturn(Optional.of(user));
        given(profileRepository.findById(4L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(4L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(4L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(4L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 4L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("UNAVAILABLE");
    }

    @Test
    @DisplayName("evaluateCandidate: CAPACITY_REACHED khi activeTicketCount >= maxActiveTickets (bằng giới hạn)")
    void evaluate_capacityExactlyReached_returnsReason() {
        AppUser user = createUser(5L, "Maxed Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 3);

        given(userRepository.findByIdWithRoles(5L)).willReturn(Optional.of(user));
        given(profileRepository.findById(5L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(5L)).willReturn(3L);
        given(skillRepository.findByTechnicianIdWithSkill(5L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(5L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 5L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("CAPACITY_REACHED");
    }

    @Test
    @DisplayName("evaluateCandidate: CAPACITY hợp lệ khi activeTicketCount < maxActiveTickets (ngay dưới giới hạn)")
    void evaluate_capacityJustBelow_passes() {
        AppUser user = createUser(6L, "Available Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 3);

        given(userRepository.findByIdWithRoles(6L)).willReturn(Optional.of(user));
        given(profileRepository.findById(6L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(6L)).willReturn(2L);
        given(skillRepository.findByTechnicianIdWithSkill(6L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(6L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 6L);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluateCandidate: SKILL_MISMATCH khi không có skill trùng khớp với requiredSkill của Ticket")
    void evaluate_skillMismatch_returnsReason() {
        AppUser user = createUser(7L, "Plumber Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(7L)).willReturn(Optional.of(user));
        given(profileRepository.findById(7L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(7L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(7L))
                .willReturn(List.of(new TechnicianSkill(profile, plumbingSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(7L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 7L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("SKILL_MISMATCH");
    }

    @Test
    @DisplayName("evaluateCandidate: SKILL_MISMATCH khi skill khớp nhưng skill.isActive() = false")
    void evaluate_inactiveSkill_returnsSkillMismatch() {
        AppUser user = createUser(8L, "Inactive Skill Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        Skill inactiveSkill = new Skill("ELEC", "Electrical");
        ReflectionTestUtils.setField(inactiveSkill, "id", 10L);
        inactiveSkill.changeActive(false);

        given(userRepository.findByIdWithRoles(8L)).willReturn(Optional.of(user));
        given(profileRepository.findById(8L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(8L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(8L))
                .willReturn(List.of(new TechnicianSkill(profile, inactiveSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(8L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 8L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("SKILL_MISMATCH");
    }

    @Test
    @DisplayName("evaluateCandidate: Ticket không yêu cầu skill (requiredSkill = null) thì không sinh SKILL_MISMATCH")
    void evaluate_nullRequiredSkill_noSkillMismatch() {
        ReflectionTestUtils.setField(ticket, "requiredSkill", null);

        AppUser user = createUser(9L, "General Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(9L)).willReturn(Optional.of(user));
        given(profileRepository.findById(9L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(9L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(9L)).willReturn(List.of()); // No skills
        given(serviceAreaRepository.findByTechnicianIdWithLocation(9L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 9L);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluateCandidate: COVERAGE_MISMATCH khi service area rỗng")
    void evaluate_emptyServiceAreas_returnsCoverageMismatch() {
        AppUser user = createUser(10L, "No Area Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(10L)).willReturn(Optional.of(user));
        given(profileRepository.findById(10L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(10L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(10L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(10L)).willReturn(List.of());

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 10L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("COVERAGE_MISMATCH");
    }

    @Test
    @DisplayName("evaluateCandidate: COVERAGE_MISMATCH khi service area trỏ đến địa điểm không hoạt động (active = false)")
    void evaluate_inactiveServiceAreaLocation_returnsCoverageMismatch() {
        AppUser user = createUser(11L, "Inactive Area Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        Location inactiveBuilding = new Location("BLD-A", "Building A", LocationType.BUILDING, campus);
        ReflectionTestUtils.setField(inactiveBuilding, "id", 2L);
        inactiveBuilding.changeActive(false);

        given(userRepository.findByIdWithRoles(11L)).willReturn(Optional.of(user));
        given(profileRepository.findById(11L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(11L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(11L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(11L))
                .willReturn(List.of(new TechnicianServiceArea(profile, inactiveBuilding)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 11L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("COVERAGE_MISMATCH");
    }

    @Test
    @DisplayName("evaluateCandidate: Coverage hợp lệ khi service area là ancestor (Building A bao phủ Room 101)")
    void evaluate_ancestorCoverage_passes() {
        AppUser user = createUser(12L, "Building Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(12L)).willReturn(Optional.of(user));
        given(profileRepository.findById(12L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(12L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(12L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(12L))
                .willReturn(List.of(new TechnicianServiceArea(profile, buildingA)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 12L);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluateCandidate: Coverage hợp lệ khi service area trùng chính xác vị trí Ticket (Room 101)")
    void evaluate_exactLocationCoverage_passes() {
        AppUser user = createUser(13L, "Room Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(13L)).willReturn(Optional.of(user));
        given(profileRepository.findById(13L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(13L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(13L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(13L))
                .willReturn(List.of(new TechnicianServiceArea(profile, room101)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 13L);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluateCandidate: Sibling location không được coi là cover (Room 102 không cover Room 101 dù cùng Building A)")
    void evaluate_siblingLocation_returnsCoverageMismatch() {
        AppUser user = createUser(14L, "Room 102 Tech", UserStatus.ACTIVE, true);
        TechnicianProfile profile = createProfile(user, true, 5);

        given(userRepository.findByIdWithRoles(14L)).willReturn(Optional.of(user));
        given(profileRepository.findById(14L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(14L)).willReturn(0L);
        given(skillRepository.findByTechnicianIdWithSkill(14L))
                .willReturn(List.of(new TechnicianSkill(profile, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdWithLocation(14L))
                .willReturn(List.of(new TechnicianServiceArea(profile, room102)));

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 14L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly("COVERAGE_MISMATCH");
    }

    @Test
    @DisplayName("evaluateCandidate: Nhiều lý do trên cùng một candidate (inactive, unavailable, capacity reached, skill mismatch, coverage mismatch)")
    void evaluate_multipleReasons_returnsAll() {
        AppUser user = createUser(15L, "Blocked Tech", UserStatus.DISABLED, false);
        TechnicianProfile profile = createProfile(user, false, 2);

        given(userRepository.findByIdWithRoles(15L)).willReturn(Optional.of(user));
        given(profileRepository.findById(15L)).willReturn(Optional.of(profile));
        given(assignmentRepository.countActiveAssignmentsByTechnicianId(15L)).willReturn(3L);
        given(skillRepository.findByTechnicianIdWithSkill(15L)).willReturn(List.of());
        given(serviceAreaRepository.findByTechnicianIdWithLocation(15L)).willReturn(List.of());

        EligibilityEvaluation result = service.evaluateCandidate(ticket, 15L);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasons()).extracting(EligibilityReason::code).containsExactly(
                "USER_INACTIVE",
                "MISSING_TECHNICIAN_ROLE",
                "UNAVAILABLE",
                "CAPACITY_REACHED",
                "SKILL_MISMATCH",
                "COVERAGE_MISMATCH");
        assertThat(result.reasons()).allMatch(r -> !r.overridable());
    }

    // ==========================================
    // 10.2 Query, Filtering, Counting & Pagination
    // ==========================================

    @Test
    @DisplayName("findEligibleTechnicians: Lọc ELIGIBLE và phân trang chuẩn xác")
    void findEligibleTechnicians_filterEligible_correctPagination() {
        AppUser eligibleUser = createUser(1L, "Alice Eligible", UserStatus.ACTIVE, true);
        AppUser blockedUser = createUser(2L, "Bob Blocked", UserStatus.ACTIVE, true);

        TechnicianProfile profile1 = createProfile(eligibleUser, true, 5);
        TechnicianProfile profile2 = createProfile(blockedUser, false, 5); // unavailable

        given(userRepository.findAllCandidateUsersWithRoles()).willReturn(List.of(eligibleUser, blockedUser));
        given(profileRepository.findAllById(anyCollection())).willReturn(List.of(profile1, profile2));
        given(skillRepository.findByTechnicianIdInWithSkill(anyCollection())).willReturn(List.of(
                new TechnicianSkill(profile1, electricalSkill, 3),
                new TechnicianSkill(profile2, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdInWithLocation(anyCollection())).willReturn(List.of(
                new TechnicianServiceArea(profile1, buildingA),
                new TechnicianServiceArea(profile2, buildingA)));
        given(assignmentRepository.countActiveAssignmentsByTechnicianIds(anyCollection())).willReturn(List.of());

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(
                PageRequest.of(0, 10),
                null,
                EligibleTechniciansQuery.EligibilityFilter.ELIGIBLE);

        PageResponse<EligibleTechnicianResponse> response = service.findEligibleTechnicians(ticket, query);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).technicianId()).isEqualTo(1L);
        assertThat(response.content().get(0).fullName()).isEqualTo("Alice Eligible");
        assertThat(response.content().get(0).eligibilityReasons()).isEmpty();
    }

    @Test
    @DisplayName("findEligibleTechnicians: Lọc BLOCKED trả về danh sách có lý do chẩn đoán")
    void findEligibleTechnicians_filterBlocked_returnsBlockedCandidates() {
        AppUser eligibleUser = createUser(1L, "Alice Eligible", UserStatus.ACTIVE, true);
        AppUser blockedUser = createUser(2L, "Bob Blocked", UserStatus.ACTIVE, true);

        TechnicianProfile profile1 = createProfile(eligibleUser, true, 5);
        TechnicianProfile profile2 = createProfile(blockedUser, false, 5); // unavailable

        given(userRepository.findAllCandidateUsersWithRoles()).willReturn(List.of(eligibleUser, blockedUser));
        given(profileRepository.findAllById(anyCollection())).willReturn(List.of(profile1, profile2));
        given(skillRepository.findByTechnicianIdInWithSkill(anyCollection())).willReturn(List.of(
                new TechnicianSkill(profile1, electricalSkill, 3),
                new TechnicianSkill(profile2, electricalSkill, 3)));
        given(serviceAreaRepository.findByTechnicianIdInWithLocation(anyCollection())).willReturn(List.of(
                new TechnicianServiceArea(profile1, buildingA),
                new TechnicianServiceArea(profile2, buildingA)));
        given(assignmentRepository.countActiveAssignmentsByTechnicianIds(anyCollection())).willReturn(List.of());

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(
                PageRequest.of(0, 10),
                null,
                EligibleTechniciansQuery.EligibilityFilter.BLOCKED);

        PageResponse<EligibleTechnicianResponse> response = service.findEligibleTechnicians(ticket, query);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).technicianId()).isEqualTo(2L);
        assertThat(response.content().get(0).eligibilityReasons())
                .extracting(EligibilityReason::code).containsExactly("UNAVAILABLE");
    }

    @Test
    @DisplayName("findEligibleTechnicians: Candidate không có profile hiển thị null capacity và mảng rỗng")
    void findEligibleTechnicians_candidateWithoutProfile_returnsNullCapacityAndEmptyArrays() {
        AppUser userWithoutProfile = createUser(3L, "Charlie NoProfile", UserStatus.ACTIVE, true);

        given(userRepository.findAllCandidateUsersWithRoles()).willReturn(List.of(userWithoutProfile));
        given(profileRepository.findAllById(anyCollection())).willReturn(List.of());
        given(skillRepository.findByTechnicianIdInWithSkill(anyCollection())).willReturn(List.of());
        given(serviceAreaRepository.findByTechnicianIdInWithLocation(anyCollection())).willReturn(List.of());
        given(assignmentRepository.countActiveAssignmentsByTechnicianIds(anyCollection())).willReturn(List.of());

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(PageRequest.of(0, 10), null, null);

        PageResponse<EligibleTechnicianResponse> response = service.findEligibleTechnicians(ticket, query);

        assertThat(response.totalElements()).isEqualTo(1);
        EligibleTechnicianResponse candidate = response.content().get(0);
        assertThat(candidate.hasProfile()).isFalse();
        assertThat(candidate.available()).isNull();
        assertThat(candidate.maxActiveTickets()).isNull();
        assertThat(candidate.skills()).isEmpty();
        assertThat(candidate.serviceAreas()).isEmpty();
        assertThat(candidate.eligibilityReasons()).extracting(EligibilityReason::code)
                .containsExactly("MISSING_TECHNICIAN_PROFILE");
    }
}
