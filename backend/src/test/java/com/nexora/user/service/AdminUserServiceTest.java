package com.nexora.user.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.user.dto.UserCreateRequest;
import com.nexora.user.dto.UserListQuery;
import com.nexora.user.dto.UserResponse;
import com.nexora.user.dto.UserRolesUpdateRequest;
import com.nexora.user.dto.UserStatusUpdateRequest;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.UserRoleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private TechnicianProfileRepository technicianProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                roleRepository,
                userRoleRepository,
                technicianProfileRepository,
                passwordEncoder
        );
    }

    @Test
    @DisplayName("createUser: Thành công, hash password, gán role và tạo TechnicianProfile nếu có role TECHNICIAN")
    void createUser_validWithTechnicianRole_success() {
        UserCreateRequest request = new UserCreateRequest(
                "tech@nexora.local",
                "Password123!",
                "Trần Kỹ Thuật",
                UserStatus.ACTIVE,
                Set.of(RoleCode.TECHNICIAN)
        );

        given(userRepository.existsByNormalizedEmail("tech@nexora.local")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("hashed_pass");

        Role role = new Role(RoleCode.TECHNICIAN, "Kỹ thuật viên");
        ReflectionTestUtils.setField(role, "id", (short) 2);
        given(roleRepository.findByCodeIn(Set.of(RoleCode.TECHNICIAN))).willReturn(List.of(role));

        given(userRepository.save(any(AppUser.class))).willAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 10L);
            ReflectionTestUtils.setField(u, "version", 0L);
            return u;
        });

        UserResponse response = service.createUser(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("tech@nexora.local");
        assertThat(response.fullName()).isEqualTo("Trần Kỹ Thuật");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.roles()).containsExactly(RoleCode.TECHNICIAN);

        verify(technicianProfileRepository).save(any(TechnicianProfile.class));
        verify(userRoleRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("createUser: Trùng normalized email ném BusinessRuleException (HTTP 422)")
    void createUser_duplicateEmail_throwsBusinessRuleException() {
        UserCreateRequest request = new UserCreateRequest(
                "DUPLICATE@nexora.local ",
                "Password123!",
                "Trần Văn A",
                UserStatus.ACTIVE,
                Set.of(RoleCode.REQUESTER)
        );

        given(userRepository.existsByNormalizedEmail("duplicate@nexora.local")).willReturn(true);

        assertThatThrownBy(() -> service.createUser(request, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Email already in use");

        verify(userRepository, never()).save(any());
        verify(technicianProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserById: Tìm thấy trả về UserResponse")
    void getUserById_found_returnsResponse() {
        AppUser user = new AppUser("user@nexora.local", "hash", "Nguyễn Văn B", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 20L);
        ReflectionTestUtils.setField(user, "version", 1L);

        given(userRepository.findByIdWithRoles(20L)).willReturn(Optional.of(user));

        UserResponse response = service.getUserById(20L);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.email()).isEqualTo("user@nexora.local");
    }

    @Test
    @DisplayName("getUserById: Không tìm thấy ném ResourceNotFoundException (HTTP 404)")
    void getUserById_notFound_throwsResourceNotFoundException() {
        given(userRepository.findByIdWithRoles(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: 999");
    }

    @Test
    @DisplayName("updateUserStatus: Cập nhật trạng thái thành công khi version khớp")
    void updateUserStatus_validVersion_updatesStatus() {
        AppUser user = new AppUser("user@nexora.local", "hash", "Nguyễn Văn B", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 20L);
        ReflectionTestUtils.setField(user, "version", 2L);

        given(userRepository.findByIdWithRoles(20L)).willReturn(Optional.of(user));
        given(userRepository.save(any(AppUser.class))).willAnswer(inv -> inv.getArgument(0));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.LOCKED, 2L);
        UserResponse response = service.updateUserStatus(20L, request);

        assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("updateUserStatus: Stale version ném StaleWriteException (HTTP 409)")
    void updateUserStatus_staleVersion_throwsStaleWriteException() {
        AppUser user = new AppUser("user@nexora.local", "hash", "Nguyễn Văn B", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 20L);
        ReflectionTestUtils.setField(user, "version", 3L);

        given(userRepository.findByIdWithRoles(20L)).willReturn(Optional.of(user));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.DISABLED, 2L);

        assertThatThrownBy(() -> service.updateUserStatus(20L, request))
                .isInstanceOf(StaleWriteException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUserRoles: Thay thế roles và tạo TechnicianProfile nếu gán thêm TECHNICIAN")
    void updateUserRoles_assignTechnician_createsProfileIfNotExists() {
        AppUser user = new AppUser("user@nexora.local", "hash", "Nguyễn Văn B", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 20L);

        given(userRepository.findByIdWithRoles(20L)).willReturn(Optional.of(user));

        Role techRole = new Role(RoleCode.TECHNICIAN, "Kỹ thuật viên");
        ReflectionTestUtils.setField(techRole, "id", (short) 2);
        given(roleRepository.findByCodeIn(Set.of(RoleCode.TECHNICIAN))).willReturn(List.of(techRole));
        given(technicianProfileRepository.existsById(20L)).willReturn(false);

        UserRolesUpdateRequest request = new UserRolesUpdateRequest(Set.of(RoleCode.TECHNICIAN));
        UserResponse response = service.updateUserRoles(20L, request, 1L);

        assertThat(response.roles()).containsExactly(RoleCode.TECHNICIAN);
        verify(userRoleRepository).deleteByUserId(20L);
        verify(userRoleRepository).saveAll(anyList());
        verify(technicianProfileRepository).save(any(TechnicianProfile.class));
    }

    @Test
    @DisplayName("listUsers: Phân trang danh sách người dùng")
    void listUsers_paged_returnsPageResponse() {
        AppUser user1 = new AppUser("u1@nexora.local", "hash", "User One", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user1, "id", 1L);
        AppUser user2 = new AppUser("u2@nexora.local", "hash", "User Two", UserStatus.LOCKED);
        ReflectionTestUtils.setField(user2, "id", 2L);

        Page<AppUser> page = new PageImpl<>(List.of(user1, user2), PageRequest.of(0, 10), 2);
        given(userRepository.findAll(any(Specification.class), any(PageRequest.class))).willReturn(page);
        given(userRepository.findAllByIdWithRoles(anyCollection())).willReturn(List.of(user1, user2));

        UserListQuery query = new UserListQuery(PageRequest.of(0, 10), null, null);
        PageResponse<UserResponse> response = service.listUsers(query);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content().get(0).email()).isEqualTo("u1@nexora.local");
        assertThat(response.content().get(1).email()).isEqualTo("u2@nexora.local");
    }
}
