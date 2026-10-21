package com.nexora.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.user.dto.TechnicianListQuery;
import com.nexora.user.dto.TechnicianSummaryResponse;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.TechnicianServiceArea;
import com.nexora.user.entity.TechnicianSkill;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;

@NexoraJpaTest
class TechnicianListRepositoryTest {

    private static final String TEST_PASSWORD = "secure-hashed-password";

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private TechnicianProfileRepository technicians;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TechnicianSkillRepository technicianSkills;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private TechnicianServiceAreaRepository technicianServiceAreas;

    @Autowired
    private TestEntityManager entityManager;

    private Role technicianRole;
    private Role managerRole;
    private Role requesterRole;
    private AppUser adminUser;

    @BeforeEach
    void setUpRoles() {
        technicianRole = roles.saveAndFlush(new Role(RoleCode.TECHNICIAN, "Kỹ thuật viên"));
        managerRole = roles.saveAndFlush(new Role(RoleCode.MANAGER, "Quản lý"));
        requesterRole = roles.saveAndFlush(new Role(RoleCode.REQUESTER, "Người báo sự cố"));

        adminUser = users.saveAndFlush(new AppUser("admin@nexora.test", TEST_PASSWORD, "Admin User", UserStatus.ACTIVE));
    }

    private AppUser createUser(String email, String fullName, UserStatus status) {
        return users.saveAndFlush(new AppUser(email, TEST_PASSWORD, fullName, status));
    }

    private void assignRole(AppUser user, Role role) {
        userRoles.saveAndFlush(new UserRole(user, role, adminUser));
    }

    private TechnicianProfile createProfile(AppUser user, boolean available, int maxCapacity) {
        return technicians.saveAndFlush(new TechnicianProfile(user, maxCapacity, available));
    }

    @Test
    @DisplayName("Lists only users with TECHNICIAN role and an existing profile; excludes users without role or without profile")
    void listsOnlyUsersWithRoleAndProfile() {
        // 1. Valid technician with role and profile
        AppUser techUser = createUser("tech@example.test", "Nguyễn Văn Tech", UserStatus.ACTIVE);
        assignRole(techUser, technicianRole);
        createProfile(techUser, true, 5);

        // 2. User with TECHNICIAN role but NO profile
        AppUser techWithoutProfile = createUser("noprofile@example.test", "Trần Không Profile", UserStatus.ACTIVE);
        assignRole(techWithoutProfile, technicianRole);

        // 3. User with profile but NO TECHNICIAN role (e.g. only REQUESTER)
        AppUser requesterWithProfile = createUser("requester@example.test", "Lê Chỉ Requester", UserStatus.ACTIVE);
        assignRole(requesterWithProfile, requesterRole);
        createProfile(requesterWithProfile, true, 3);

        entityManager.flush();
        entityManager.clear();

        TechnicianListQuery query = TechnicianListQuery.from(new LinkedMultiValueMap<>());
        Page<TechnicianProfile> page = technicians.findAll(query.toSpecification(), query.pageable());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTechnicianId()).isEqualTo(techUser.getId());
        assertThat(page.getContent().get(0).getUser().getFullName()).isEqualTo("Nguyễn Văn Tech");
    }

    @Test
    @DisplayName("LOCKED and DISABLED users with valid profile and role remain visible")
    void lockedAndDisabledUsersRemainVisible() {
        AppUser activeUser = createUser("active@example.test", "An Hoạt Động", UserStatus.ACTIVE);
        assignRole(activeUser, technicianRole);
        createProfile(activeUser, true, 5);

        AppUser lockedUser = createUser("locked@example.test", "Bình Bị Khoá", UserStatus.LOCKED);
        assignRole(lockedUser, technicianRole);
        createProfile(lockedUser, true, 4);

        AppUser disabledUser = createUser("disabled@example.test", "Cường Vô Hiệu", UserStatus.DISABLED);
        assignRole(disabledUser, technicianRole);
        createProfile(disabledUser, false, 2);

        entityManager.flush();
        entityManager.clear();

        TechnicianListQuery query = TechnicianListQuery.from(new LinkedMultiValueMap<>());
        Page<TechnicianProfile> page = technicians.findAll(query.toSpecification(), query.pageable());

        assertThat(page.getTotalElements()).isEqualTo(3);
        List<UserStatus> statuses = page.getContent().stream()
                .map(tp -> tp.getUser().getStatus())
                .toList();
        assertThat(statuses).containsExactlyInAnyOrder(UserStatus.ACTIVE, UserStatus.LOCKED, UserStatus.DISABLED);
    }

    @Test
    @DisplayName("Multiple roles and multiple skills/areas do not duplicate technician rows")
    void multipleRolesAndSkillsDoNotDuplicateRows() {
        AppUser user = createUser("multi@example.test", "Đa Năng Tech", UserStatus.ACTIVE);
        assignRole(user, technicianRole);
        assignRole(user, managerRole);
        assignRole(user, requesterRole);
        TechnicianProfile profile = createProfile(user, true, 5);

        // Add 2 skills
        Skill skill1 = skills.saveAndFlush(new Skill("ELEC", "Điện"));
        Skill skill2 = skills.saveAndFlush(new Skill("HVAC", "Điều hòa"));
        technicianSkills.saveAndFlush(new TechnicianSkill(profile, skill1, 4));
        technicianSkills.saveAndFlush(new TechnicianSkill(profile, skill2, 5));

        // Add 2 service areas
        Location loc1 = locations.saveAndFlush(new Location("BLD_A", "Tòa A", LocationType.BUILDING, null));
        Location loc2 = locations.saveAndFlush(new Location("BLD_B", "Tòa B", LocationType.BUILDING, null));
        technicianServiceAreas.saveAndFlush(new TechnicianServiceArea(profile, loc1));
        technicianServiceAreas.saveAndFlush(new TechnicianServiceArea(profile, loc2));

        entityManager.flush();
        entityManager.clear();

        TechnicianListQuery query = TechnicianListQuery.from(new LinkedMultiValueMap<>());
        Page<TechnicianProfile> page = technicians.findAll(query.toSpecification(), query.pageable());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTechnicianId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Combines search query and availability filters with AND semantics")
    void searchAndAvailabilityFiltersCombined() {
        AppUser u1 = createUser("an.tech@example.test", "Nguyễn An", UserStatus.ACTIVE);
        assignRole(u1, technicianRole);
        createProfile(u1, true, 5);

        AppUser u2 = createUser("an.busy@example.test", "Phạm An", UserStatus.ACTIVE);
        assignRole(u2, technicianRole);
        createProfile(u2, false, 3);

        AppUser u3 = createUser("binh.tech@example.test", "Trần Bình", UserStatus.ACTIVE);
        assignRole(u3, technicianRole);
        createProfile(u3, true, 4);

        entityManager.flush();
        entityManager.clear();

        // Filter: query=an AND available=true -> only Nguyễn An
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "an");
        params.add("available", "true");

        TechnicianListQuery query = TechnicianListQuery.from(params);
        Page<TechnicianProfile> page = technicians.findAll(query.toSpecification(), query.pageable());

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getUser().getFullName()).isEqualTo("Nguyễn An");
        assertThat(page.getContent().get(0).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Case-insensitive substring search matches fullName or email")
    void caseInsensitiveSearchMatchesNameOrEmail() {
        AppUser u1 = createUser("hoang.technician@nexora.campus", "Hoàng Nam", UserStatus.ACTIVE);
        assignRole(u1, technicianRole);
        createProfile(u1, true, 5);

        AppUser u2 = createUser("minh.tri@nexora.campus", "Võ Minh Trí", UserStatus.ACTIVE);
        assignRole(u2, technicianRole);
        createProfile(u2, true, 3);

        entityManager.flush();
        entityManager.clear();

        // Search by email substring (uppercase)
        MultiValueMap<String, String> emailParams = new LinkedMultiValueMap<>();
        emailParams.add("query", "TECHNICIAN");
        Page<TechnicianProfile> emailResult = technicians.findAll(
                TechnicianListQuery.from(emailParams).toSpecification(),
                TechnicianListQuery.from(emailParams).pageable());
        assertThat(emailResult.getTotalElements()).isEqualTo(1);
        assertThat(emailResult.getContent().get(0).getUser().getFullName()).isEqualTo("Hoàng Nam");

        // Search by accented name substring
        MultiValueMap<String, String> nameParams = new LinkedMultiValueMap<>();
        nameParams.add("query", "Trí");
        Page<TechnicianProfile> nameResult = technicians.findAll(
                TechnicianListQuery.from(nameParams).toSpecification(),
                TechnicianListQuery.from(nameParams).pageable());
        assertThat(nameResult.getTotalElements()).isEqualTo(1);
        assertThat(nameResult.getContent().get(0).getUser().getFullName()).isEqualTo("Võ Minh Trí");
    }

    @Test
    @DisplayName("Deterministic ordering sorts by fullName ASC, then technicianId ASC")
    void deterministicOrdering() {
        AppUser u1 = createUser("b@example.test", "Đặng Văn B", UserStatus.ACTIVE);
        assignRole(u1, technicianRole);
        createProfile(u1, true, 5);

        AppUser u2 = createUser("a2@example.test", "Đặng Văn A", UserStatus.ACTIVE);
        assignRole(u2, technicianRole);
        createProfile(u2, true, 5);

        AppUser u3 = createUser("a1@example.test", "Đặng Văn A", UserStatus.ACTIVE);
        assignRole(u3, technicianRole);
        createProfile(u3, true, 5);

        entityManager.flush();
        entityManager.clear();

        TechnicianListQuery query = TechnicianListQuery.from(new LinkedMultiValueMap<>());
        Page<TechnicianProfile> page = technicians.findAll(query.toSpecification(), query.pageable());

        assertThat(page.getTotalElements()).isEqualTo(3);
        List<TechnicianProfile> content = page.getContent();
        // Both "Đặng Văn A" first, ordered by technicianId ascending
        assertThat(content.get(0).getUser().getFullName()).isEqualTo("Đặng Văn A");
        assertThat(content.get(1).getUser().getFullName()).isEqualTo("Đặng Văn A");
        assertThat(content.get(0).getTechnicianId()).isLessThan(content.get(1).getTechnicianId());
        assertThat(content.get(2).getUser().getFullName()).isEqualTo("Đặng Văn B");
    }

    @Test
    @DisplayName("Pagination semantics: empty results and page beyond available range")
    void paginationSemantics() {
        AppUser u = createUser("one@example.test", "Duy Nhất", UserStatus.ACTIVE);
        assignRole(u, technicianRole);
        createProfile(u, true, 5);

        entityManager.flush();
        entityManager.clear();

        // Out-of-range page (page=5, size=20)
        MultiValueMap<String, String> outOfRangeParams = new LinkedMultiValueMap<>();
        outOfRangeParams.add("page", "5");
        outOfRangeParams.add("size", "20");
        TechnicianListQuery outOfRangeQuery = TechnicianListQuery.from(outOfRangeParams);
        Page<TechnicianProfile> outOfRangePage = technicians.findAll(
                outOfRangeQuery.toSpecification(), outOfRangeQuery.pageable());
        PageResponse<TechnicianSummaryResponse> outOfRangeResponse = PageResponse.from(
                outOfRangePage.map(TechnicianSummaryResponse::from));

        assertThat(outOfRangeResponse.content()).isEmpty();
        assertThat(outOfRangeResponse.totalElements()).isEqualTo(1);
        assertThat(outOfRangeResponse.totalPages()).isEqualTo(1);
        assertThat(outOfRangeResponse.page()).isEqualTo(5);

        // No match query
        MultiValueMap<String, String> noMatchParams = new LinkedMultiValueMap<>();
        noMatchParams.add("query", "nonexistent-technician-query");
        TechnicianListQuery noMatchQuery = TechnicianListQuery.from(noMatchParams);
        Page<TechnicianProfile> noMatchPage = technicians.findAll(
                noMatchQuery.toSpecification(), noMatchQuery.pageable());
        PageResponse<TechnicianSummaryResponse> noMatchResponse = PageResponse.from(
                noMatchPage.map(TechnicianSummaryResponse::from));

        assertThat(noMatchResponse.content()).isEmpty();
        assertThat(noMatchResponse.totalElements()).isZero();
        assertThat(noMatchResponse.totalPages()).isZero();
        assertThat(noMatchResponse.page()).isZero();
    }

    @Test
    @DisplayName("DTO mapping maps public fields and does not leak sensitive information")
    void dtoMappingIntegrity() {
        AppUser u = createUser("dto.test@example.test", "Nguyễn DTO", UserStatus.ACTIVE);
        assignRole(u, technicianRole);
        TechnicianProfile profile = createProfile(u, true, 8);

        entityManager.flush();
        entityManager.clear();

        TechnicianProfile loaded = technicians.findById(profile.getTechnicianId()).orElseThrow();
        TechnicianSummaryResponse dto = TechnicianSummaryResponse.from(loaded);

        assertThat(dto.technicianId()).isEqualTo(u.getId());
        assertThat(dto.fullName()).isEqualTo("Nguyễn DTO");
        assertThat(dto.email()).isEqualTo("dto.test@example.test");
        assertThat(dto.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(dto.available()).isTrue();
        assertThat(dto.maxActiveTickets()).isEqualTo(8);
        assertThat(dto.version()).isEqualTo(0L);
    }
}
