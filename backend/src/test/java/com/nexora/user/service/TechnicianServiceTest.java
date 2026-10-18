package com.nexora.user.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.dto.SkillAssignmentItem;
import com.nexora.user.dto.TechnicianListQuery;
import com.nexora.user.dto.TechnicianProfileResponse;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.TechnicianSkillsUpdateRequest;
import com.nexora.user.dto.TechnicianUpdateRequest;
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
import com.nexora.user.repository.SkillRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.TechnicianServiceAreaRepository;
import com.nexora.user.repository.TechnicianSkillRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TechnicianServiceTest {

    @Mock
    private TechnicianProfileRepository technicianProfileRepository;

    @Mock
    private TechnicianSkillRepository technicianSkillRepository;

    @Mock
    private TechnicianServiceAreaRepository technicianServiceAreaRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private com.nexora.auth.service.AuthService authService;

    private TechnicianService service;

    @BeforeEach
    void setUp() {
        service = new TechnicianService(
                technicianProfileRepository,
                technicianSkillRepository,
                technicianServiceAreaRepository,
                userRepository,
                skillRepository,
                locationRepository,
                authService
        );
    }

    private AppUser createTechnicianUser(Long id) {
        AppUser user = new AppUser("tech@nexora.local", "hash", "Kỹ thuật viên A", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        Role role = new Role(RoleCode.TECHNICIAN, "Kỹ thuật viên");
        ReflectionTestUtils.setField(role, "id", (short) 2);
        UserRole ur = new UserRole(user, role, null);
        ReflectionTestUtils.setField(user, "roleAssignments", List.of(ur));
        return user;
    }

    @Test
    @DisplayName("getProfile: Manager/Admin có thể xem profile bất kỳ technician nào")
    void getProfile_byManagerOrAdmin_success() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        ReflectionTestUtils.setField(profile, "version", 0L);

        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));
        given(technicianSkillRepository.findByTechnicianIdWithSkill(5L)).willReturn(List.of());
        given(technicianServiceAreaRepository.findByTechnicianIdWithLocation(5L)).willReturn(List.of());

        TechnicianProfileResponse response = service.getProfile(5L, 1L, true);

        assertThat(response).isNotNull();
        assertThat(response.technicianId()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("tech@nexora.local");
    }

    @Test
    @DisplayName("getProfile: Technician có thể xem profile của chính mình")
    void getProfile_byTechnicianSelf_success() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        ReflectionTestUtils.setField(profile, "version", 0L);

        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));
        given(technicianSkillRepository.findByTechnicianIdWithSkill(5L)).willReturn(List.of());
        given(technicianServiceAreaRepository.findByTechnicianIdWithLocation(5L)).willReturn(List.of());

        TechnicianProfileResponse response = service.getProfile(5L, 5L, false);

        assertThat(response.technicianId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getProfile: Technician xem profile của người khác bị AccessDeniedException (HTTP 403)")
    void getProfile_byOtherTechnician_throwsAccessDeniedException() {
        assertThatThrownBy(() -> service.getProfile(5L, 6L, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Technicians can only access their own profile");

        verify(technicianProfileRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getProfile: Không tìm thấy profile ném ResourceNotFoundException (HTTP 404)")
    void getProfile_notFound_throwsResourceNotFoundException() {
        given(technicianProfileRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(999L, 1L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Technician profile not found for id: 999");
    }

    @Test
    @DisplayName("updateProfile: User thiếu role TECHNICIAN ném BusinessRuleException (HTTP 422)")
    void updateProfile_userNotTechnician_throwsBusinessRuleException() {
        AppUser nonTechUser = new AppUser("user@nexora.local", "hash", "Người yêu cầu", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(nonTechUser, "id", 10L);
        Role requesterRole = new Role(RoleCode.REQUESTER, "Người yêu cầu");
        ReflectionTestUtils.setField(requesterRole, "id", (short) 1);
        UserRole ur = new UserRole(nonTechUser, requesterRole, null);
        ReflectionTestUtils.setField(nonTechUser, "roleAssignments", List.of(ur));

        given(userRepository.findByIdWithRoles(10L)).willReturn(Optional.of(nonTechUser));

        TechnicianUpdateRequest request = new TechnicianUpdateRequest(true, 5, null);

        assertThatThrownBy(() -> service.updateProfile(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not have the TECHNICIAN role");

        verify(technicianProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: Stale version ném StaleWriteException (HTTP 409)")
    void updateProfile_staleVersion_throwsStaleWriteException() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        ReflectionTestUtils.setField(profile, "version", 2L);

        given(userRepository.findByIdWithRoles(5L)).willReturn(Optional.of(user));
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        TechnicianUpdateRequest request = new TechnicianUpdateRequest(true, 5, 1L);

        assertThatThrownBy(() -> service.updateProfile(5L, request))
                .isInstanceOf(StaleWriteException.class);

        verify(technicianProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: Thành công khi user là technician và version khớp")
    void updateProfile_valid_success() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        ReflectionTestUtils.setField(profile, "version", 1L);

        given(userRepository.findByIdWithRoles(5L)).willReturn(Optional.of(user));
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));
        given(technicianProfileRepository.save(any(TechnicianProfile.class))).willAnswer(inv -> inv.getArgument(0));

        TechnicianUpdateRequest request = new TechnicianUpdateRequest(false, 8, 1L);
        TechnicianProfileResponse response = service.updateProfile(5L, request);

        assertThat(response.available()).isFalse();
        assertThat(response.maxActiveTickets()).isEqualTo(8);
    }

    @Test
    @DisplayName("updateSkills: Thành công thay thế danh sách kỹ năng")
    void updateSkills_valid_replacesSkills() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        Skill skill1 = new Skill("ELECTRIC", "Điện");
        ReflectionTestUtils.setField(skill1, "id", 1L);
        Skill skill2 = new Skill("PLUMBING", "Ống nước");
        ReflectionTestUtils.setField(skill2, "id", 2L);

        given(skillRepository.findAllById(Set.of(1L, 2L))).willReturn(List.of(skill1, skill2));

        TechnicianSkillsUpdateRequest request = new TechnicianSkillsUpdateRequest(
                List.of(
                        new SkillAssignmentItem(1L, 4),
                        new SkillAssignmentItem(2L, 5)
                ),
                null
        );

        TechnicianProfileResponse response = service.updateSkills(5L, request);

        assertThat(response).isNotNull();
        verify(technicianSkillRepository).deleteByTechnicianId(5L);
        verify(technicianSkillRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("updateSkills: Trùng lặp skillId trong cùng 1 request ném BusinessRuleException (HTTP 422)")
    void updateSkills_duplicateSkill_throwsBusinessRuleException() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        TechnicianSkillsUpdateRequest request = new TechnicianSkillsUpdateRequest(
                List.of(
                        new SkillAssignmentItem(1L, 4),
                        new SkillAssignmentItem(1L, 5)
                ),
                null
        );

        assertThatThrownBy(() -> service.updateSkills(5L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate skill in request");

        verify(technicianSkillRepository, never()).deleteByTechnicianId(any());
    }

    @Test
    @DisplayName("updateSkills: SkillId không tồn tại ném ResourceNotFoundException (HTTP 404)")
    void updateSkills_skillNotFound_throwsResourceNotFoundException() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        given(skillRepository.findAllById(Set.of(99L))).willReturn(List.of());

        TechnicianSkillsUpdateRequest request = new TechnicianSkillsUpdateRequest(
                List.of(new SkillAssignmentItem(99L, 3)),
                null
        );

        assertThatThrownBy(() -> service.updateSkills(5L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Skills not found");

        verify(technicianSkillRepository, never()).deleteByTechnicianId(any());
    }

    @Test
    @DisplayName("updateServiceAreas: Thành công thay thế khu vực phục vụ khi location tồn tại")
    void updateServiceAreas_valid_replacesAreas() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        Location loc1 = new Location("BLD_A", "Tòa nhà A", LocationType.BUILDING, null);
        ReflectionTestUtils.setField(loc1, "id", 10L);

        given(locationRepository.findAllById(Set.of(10L))).willReturn(List.of(loc1));

        TechnicianServiceAreasUpdateRequest request = new TechnicianServiceAreasUpdateRequest(List.of(10L), null);
        TechnicianProfileResponse response = service.updateServiceAreas(5L, request);

        assertThat(response).isNotNull();
        verify(technicianServiceAreaRepository).deleteByTechnicianId(5L);
        verify(technicianServiceAreaRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("updateServiceAreas: Location không tồn tại ném ResourceNotFoundException (HTTP 404)")
    void updateServiceAreas_locationNotFound_throwsResourceNotFoundException() {
        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        given(technicianProfileRepository.findById(5L)).willReturn(Optional.of(profile));

        given(locationRepository.findAllById(Set.of(999L))).willReturn(List.of());

        TechnicianServiceAreasUpdateRequest request = new TechnicianServiceAreasUpdateRequest(List.of(999L), null);

        assertThatThrownBy(() -> service.updateServiceAreas(5L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Locations not found");

        verify(technicianServiceAreaRepository, never()).deleteByTechnicianId(any());
    }

    @Test
    @DisplayName("listTechnicians: Manager or Admin can list technicians (200 OK)")
    void listTechnicians_managerOrAdmin_success() {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.Jwt.class);
        com.nexora.auth.dto.response.CurrentUserResponse managerUser = new com.nexora.auth.dto.response.CurrentUserResponse(
                1L, "mgr@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerUser);

        AppUser user = createTechnicianUser(5L);
        TechnicianProfile profile = new TechnicianProfile(user);
        org.springframework.data.domain.Page<TechnicianProfile> page =
                new org.springframework.data.domain.PageImpl<>(List.of(profile));
        given(technicianProfileRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .willReturn(page);

        TechnicianListQuery query = TechnicianListQuery.from(new org.springframework.util.LinkedMultiValueMap<>());
        com.nexora.common.pagination.PageResponse<com.nexora.user.dto.TechnicianSummaryResponse> response =
                service.listTechnicians(jwt, query);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).technicianId()).isEqualTo(5L);
        assertThat(response.content().get(0).fullName()).isEqualTo("Kỹ thuật viên A");
    }

    @Test
    @DisplayName("listTechnicians: Caller without MANAGER or ADMIN throws AccessDeniedException (403)")
    void listTechnicians_unauthorizedRole_throwsAccessDeniedException() {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.mockito.Mockito.mock(org.springframework.security.oauth2.jwt.Jwt.class);
        com.nexora.auth.dto.response.CurrentUserResponse techOnly = new com.nexora.auth.dto.response.CurrentUserResponse(
                5L, "tech@nexora.local", "Tech Only", "ACTIVE", List.of("TECHNICIAN"));
        given(authService.getCurrentUser(jwt)).willReturn(techOnly);

        TechnicianListQuery query = TechnicianListQuery.from(new org.springframework.util.LinkedMultiValueMap<>());

        assertThatThrownBy(() -> service.listTechnicians(jwt, query))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only managers and admins");
    }

    @Test
    @DisplayName("listTechnicians: Null JWT throws BadCredentialsException (401)")
    void listTechnicians_nullJwt_throwsBadCredentialsException() {
        TechnicianListQuery query = TechnicianListQuery.from(new org.springframework.util.LinkedMultiValueMap<>());

        assertThatThrownBy(() -> service.listTechnicians(null, query))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
}

