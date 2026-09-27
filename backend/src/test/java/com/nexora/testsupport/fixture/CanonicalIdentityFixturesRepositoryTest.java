package com.nexora.testsupport.fixture;

import java.util.Map;

import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.testsupport.reset.OwnedTestDataReset;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@NexoraJpaTest
class CanonicalIdentityFixturesRepositoryTest {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private CanonicalIdentityFixtures fixtures;
    private OwnedTestDataReset reset;

    @BeforeEach
    void setUp() {
        fixtures = new CanonicalIdentityFixtures(userRepository, roleRepository, userRoleRepository);
        reset = new OwnedTestDataReset(userRepository, roleRepository, userRoleRepository);
        reset.resetIdentityData();
    }

    @Test
    void persistsUsersAndAssignmentsForAllCanonicalRolesOnFastH2Profile() {
        Map<RoleCode, AppUser> users = fixtures.createUsersForEveryCanonicalRole();

        assertThat(users).containsOnlyKeys(RoleCode.values());
        assertThat(userRepository.count()).isEqualTo(4);
        assertThat(roleRepository.count()).isEqualTo(4);
        assertThat(userRoleRepository.count()).isEqualTo(4);
        assertThat(users.values())
                .allSatisfy(user -> assertThat(user.getId()).isNotNull());
    }

    @Test
    void resetDeletesOnlyOwnedIdentityTablesInForeignKeySafeOrder() {
        fixtures.createUsersForEveryCanonicalRole();

        reset.resetIdentityData();

        assertThat(userRoleRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
        assertThat(roleRepository.count()).isZero();
    }
}
