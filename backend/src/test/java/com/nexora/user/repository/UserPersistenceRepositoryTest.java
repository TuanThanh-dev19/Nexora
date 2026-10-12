package com.nexora.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;

import jakarta.persistence.Persistence;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:userPersistence;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS dbo"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPersistenceRepositoryTest {

    private static final String TEST_PASSWORD_HASH = "test-only-password-hash";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndRetrievesUserById() {
        AppUser saved = appUserRepository.saveAndFlush(new AppUser(
                "requester@example.test", TEST_PASSWORD_HASH, "Test Requester"));

        entityManager.clear();

        AppUser reloaded = appUserRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("requester@example.test");
        assertThat(reloaded.getFullName()).isEqualTo("Test Requester");
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void resolvesRoleByCanonicalCode() {
        roleRepository.saveAndFlush(new Role(RoleCode.TECHNICIAN, "Technician"));

        entityManager.clear();

        assertThat(roleRepository.findByCode(RoleCode.TECHNICIAN))
                .get()
                .extracting(Role::getName)
                .isEqualTo("Technician");
    }

    @Test
    void loadsMultipleRolesAndAssignmentMetadata() {
        Role requester = roleRepository.saveAndFlush(new Role(RoleCode.REQUESTER, "Requester"));
        Role manager = roleRepository.saveAndFlush(new Role(RoleCode.MANAGER, "Manager"));
        AppUser assigner = appUserRepository.saveAndFlush(new AppUser(
                "admin@example.test", TEST_PASSWORD_HASH, "Test Admin", UserStatus.ACTIVE));
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
                "multi-role@example.test", TEST_PASSWORD_HASH, "Multi Role User"));
        OffsetDateTime assignedAt = OffsetDateTime.of(
                2026, 9, 26, 8, 30, 0, 0, ZoneOffset.UTC);

        userRoleRepository.save(new UserRole(user, requester, null, assignedAt));
        userRoleRepository.saveAndFlush(new UserRole(user, manager, assigner, assignedAt.plusMinutes(1)));
        entityManager.clear();

        AppUser loaded = appUserRepository.findByIdWithRoles(user.getId()).orElseThrow();
        assertThat(Persistence.getPersistenceUtil().isLoaded(loaded, "roleAssignments")).isTrue();
        assertThat(loaded.getRoleAssignments())
                .allSatisfy(assignment -> assertThat(
                        Persistence.getPersistenceUtil().isLoaded(assignment, "role")).isTrue());
        entityManager.clear();

        assertThat(loaded.getRoleAssignments())
                .extracting(assignment -> assignment.getRole().getCode())
                .containsExactlyInAnyOrder(RoleCode.REQUESTER, RoleCode.MANAGER);
        assertThat(loaded.getRoleAssignments())
                .extracting(UserRole::getAssignedAt)
                .containsExactlyInAnyOrder(assignedAt, assignedAt.plusMinutes(1));
        assertThat(loaded.getRoleAssignments())
                .filteredOn(assignment -> assignment.getRole().getCode() == RoleCode.REQUESTER)
                .singleElement()
                .extracting(UserRole::getAssignedBy)
                .isNull();
        assertThat(loaded.getRoleAssignments())
                .filteredOn(assignment -> assignment.getRole().getCode() == RoleCode.MANAGER)
                .singleElement()
                .extracting(assignment -> assignment.getAssignedBy().getId())
                .isEqualTo(assigner.getId());
    }

    @Test
    void incrementsVersionWhenUserChanges() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
                "versioned@example.test", TEST_PASSWORD_HASH, "Before Update"));
        Long initialVersion = user.getVersion();

        user.changeFullName("After Update");
        appUserRepository.flush();

        assertThat(user.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(user.getCreatedAt());
    }

    @Test
    void rejectsAStaleUserUpdate() {
        AppUser staleUser = appUserRepository.saveAndFlush(new AppUser(
                "stale@example.test", TEST_PASSWORD_HASH, "Original Name"));
        entityManager.detach(staleUser);

        jdbcTemplate.update("""
                UPDATE dbo.app_users
                SET full_name = ?, version = version + 1
                WHERE id = ?
                """, "Concurrent Update", staleUser.getId());
        staleUser.changeFullName("Stale Update");

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(staleUser))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
