package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class UserPersistenceSqlServerIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "test-only-password-hash";

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void databaseGeneratesNormalizedEmailAndRepositoryFindsIt() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
                "  Mixed.Case@Example.Test  ", TEST_PASSWORD_HASH, "Normalized Email User"));

        entityManager.refresh(user);

        assertThat(user.getNormalizedEmail()).isEqualTo("mixed.case@example.test");
        assertThat(appUserRepository.findByNormalizedEmail("mixed.case@example.test"))
                .get()
                .extracting(AppUser::getId)
                .isEqualTo(user.getId());
    }

    @Test
    void loadsMultipleSeededRolesWithAssignmentMetadata() {
        Role requester = roleRepository.findByCode(RoleCode.REQUESTER).orElseThrow();
        Role manager = roleRepository.findByCode(RoleCode.MANAGER).orElseThrow();
        AppUser assigner = appUserRepository.saveAndFlush(new AppUser(
                "sql-assigner@example.test", TEST_PASSWORD_HASH, "SQL Assigner"));
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
                "sql-multi-role@example.test", TEST_PASSWORD_HASH, "SQL Multi Role"));
        OffsetDateTime assignedAt = OffsetDateTime.of(
                2026, 9, 26, 9, 0, 0, 123_456_700, ZoneOffset.ofHours(7));

        userRoleRepository.save(new UserRole(user, requester, null, assignedAt));
        userRoleRepository.saveAndFlush(new UserRole(
                user, manager, assigner, assignedAt.plusMinutes(1)));
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
    void rejectsEmailsWithTheSameNormalizedValue() {
        appUserRepository.saveAndFlush(new AppUser(
                " duplicate@example.test ", TEST_PASSWORD_HASH, "First Duplicate"));

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(new AppUser(
                "DUPLICATE@EXAMPLE.TEST", TEST_PASSWORD_HASH, "Second Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
