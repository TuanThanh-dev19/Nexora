package com.nexora.testsupport.fixture;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;

public final class CanonicalIdentityFixtures {

    private static final OffsetDateTime ASSIGNED_AT = OffsetDateTime.of(
            2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public CanonicalIdentityFixtures(
            AppUserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public Map<RoleCode, AppUser> createUsersForEveryCanonicalRole() {
        Map<RoleCode, AppUser> users = new EnumMap<>(RoleCode.class);
        for (RoleCode code : RoleCode.values()) {
            Role role = roleRepository.saveAndFlush(RoleFixtures.role(code));
            AppUser user = userRepository.saveAndFlush(UserFixtures.userFor(code));
            userRoleRepository.saveAndFlush(new UserRole(user, role, null, ASSIGNED_AT));
            users.put(code, user);
        }
        return Map.copyOf(users);
    }
}
