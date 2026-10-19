package com.nexora.user.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.repository.UserRoleRepository;

@Service
@Transactional
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TechnicianProfileRepository technicianProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            AppUserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            TechnicianProfileRepository technicianProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.technicianProfileRepository = technicianProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(UserListQuery query) {
        Page<AppUser> userPage = userRepository.findAll(query.toSpecification(), query.pageable());
        List<Long> ids = userPage.getContent().stream().map(AppUser::getId).toList();

        if (ids.isEmpty()) {
            return new PageResponse<>(List.of(), userPage.getNumber(), userPage.getSize(),
                    userPage.getTotalElements(), userPage.getTotalPages());
        }

        List<AppUser> usersWithRoles = userRepository.findAllByIdWithRoles(ids);
        Map<Long, AppUser> userMap = usersWithRoles.stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<UserResponse> responses = userPage.getContent().stream()
                .map(u -> UserResponse.from(userMap.getOrDefault(u.getId(), u)))
                .toList();

        return new PageResponse<>(responses, userPage.getNumber(), userPage.getSize(),
                userPage.getTotalElements(), userPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        AppUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    public UserResponse createUser(UserCreateRequest request, Long adminId) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            log.warn("User creation rejected: email already exists (hash={})", normalizedEmail.hashCode());
            throw new BusinessRuleException("Email already in use: " + request.email());
        }

        String passwordHash = passwordEncoder.encode(request.password());
        AppUser user = new AppUser(
                request.email().trim(),
                passwordHash,
                request.fullName().trim(),
                request.resolvedStatus()
        );
        user = userRepository.save(user);

        AppUser adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        List<Role> roles = roleRepository.findByCodeIn(request.roles());
        List<UserRole> userRoles = new ArrayList<>();
        for (Role role : roles) {
            userRoles.add(new UserRole(user, role, adminUser));
        }
        userRoleRepository.saveAll(userRoles);

        if (request.roles().contains(RoleCode.TECHNICIAN)) {
            technicianProfileRepository.save(new TechnicianProfile(user));
            log.info("Initialized default technician profile for new user id={}", user.getId());
        }

        List<RoleCode> roleCodes = roles.stream().map(Role::getCode).toList();
        log.info("Created user id={} emailHash={} roles={}", user.getId(), normalizedEmail.hashCode(), roleCodes);
        return UserResponse.from(user, roleCodes);
    }

    public UserResponse updateUserStatus(Long userId, UserStatusUpdateRequest request) {
        // Serialize eligibility changes with assignment before loading the target user's state.
        technicianProfileRepository.findByIdForUpdate(userId);
        AppUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.version() != null && !Objects.equals(user.getVersion(), request.version())) {
            log.warn("Optimistic locking conflict on user id={}: expected={} current={}",
                    userId, request.version(), user.getVersion());
            throw new StaleWriteException();
        }

        user.changeStatus(request.status());
        user = userRepository.save(user);
        log.info("Updated status for user id={} to {}", userId, request.status());
        return UserResponse.from(user);
    }

    public UserResponse updateUserRoles(Long userId, UserRolesUpdateRequest request, Long adminId) {
        technicianProfileRepository.findByIdForUpdate(userId);
        AppUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        AppUser adminUser = adminId != null ? userRepository.findById(adminId).orElse(null) : null;

        List<Role> roles = roleRepository.findByCodeIn(request.roles());
        userRoleRepository.deleteByUserId(userId);

        List<UserRole> newRoles = roles.stream()
                .map(r -> new UserRole(user, r, adminUser))
                .toList();
        userRoleRepository.saveAll(newRoles);

        if (request.roles().contains(RoleCode.TECHNICIAN) && !technicianProfileRepository.existsById(userId)) {
            technicianProfileRepository.save(new TechnicianProfile(user));
            log.info("Initialized default technician profile for user id={}", userId);
        }

        List<RoleCode> roleCodes = roles.stream().map(Role::getCode).toList();
        log.info("Updated roles for user id={} to {}", userId, roleCodes);
        return UserResponse.from(user, roleCodes);
    }
}
