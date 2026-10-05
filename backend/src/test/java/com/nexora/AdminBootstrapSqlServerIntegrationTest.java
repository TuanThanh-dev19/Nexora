package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.auth.bootstrap.AdminBootstrapService;
import com.nexora.auth.dto.request.LoginRequest;
import com.nexora.auth.dto.response.LoginResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class AdminBootstrapSqlServerIntegrationTest {

    private static final String EMAIL = "bootstrap-admin@example.test";
    private static final String PASSWORD = "test-only-password";

    @Autowired
    private AdminBootstrapService bootstrapService;
    @Autowired
    private AuthService authService;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @MockitoSpyBean
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private EntityManager entityManager;

    @Test
    void bootstrapPersistsAnAdminWhoCanLogInWithARealBcryptPassword() {
        assertThat(bootstrapService.createInitialAdmin("  " + EMAIL + "  ", PASSWORD)).isTrue();
        entityManager.clear();

        AppUser admin = userRepository.findByNormalizedEmailWithRoles(EMAIL).orElseThrow();
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getNormalizedEmail()).isEqualTo(EMAIL);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(admin.getRoleAssignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.getRole().getCode()).isEqualTo(RoleCode.ADMIN);
            assertThat(assignment.getAssignedBy()).isNull();
        });

        LoginResponse login = authService.login(new LoginRequest(EMAIL, PASSWORD));
        assertThat(login.user().roles()).containsExactly("ADMIN");
        assertThat(jwtDecoder.decode(login.accessToken()).getSubject()).isEqualTo(admin.getId().toString());
    }

    @Test
    void repeatBootstrapDoesNotResetPasswordOrReactivateADisabledAdmin() {
        bootstrapService.createInitialAdmin(EMAIL, PASSWORD);
        AppUser admin = userRepository.findByNormalizedEmail(EMAIL).orElseThrow();
        String originalHash = admin.getPasswordHash();
        admin.changeStatus(UserStatus.DISABLED);
        userRepository.flush();
        long userCount = userRepository.count();

        assertThat(bootstrapService.createInitialAdmin("another-admin@example.test", "another-password"))
                .isFalse();
        entityManager.clear();

        AppUser reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isEqualTo(originalHash);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(userRepository.count()).isEqualTo(userCount);
    }

    @Test
    void existingNormalizedEmailCannotBePromotedToAdmin() {
        AppUser existing = userRepository.saveAndFlush(new AppUser(
                "  Existing@Example.Test  ", "test-only-hash", "Existing Requester"));
        userRoleRepository.saveAndFlush(new UserRole(existing,
                roleRepository.findByCode(RoleCode.REQUESTER).orElseThrow(), null));

        assertThatThrownBy(() -> bootstrapService.createInitialAdmin("EXISTING@EXAMPLE.TEST", PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs to an existing account");

        assertThat(userRoleRepository.existsByRole_Code(RoleCode.ADMIN)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void roleAssignmentFailureRollsBackTheNewUser() {
        doThrow(new DataIntegrityViolationException("Simulated assignment failure"))
                .when(userRoleRepository).saveAndFlush(any(UserRole.class));

        assertThatThrownBy(() -> bootstrapService.createInitialAdmin(EMAIL, PASSWORD))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(userRepository.findByNormalizedEmail(EMAIL)).isEmpty();
        assertThat(userRoleRepository.existsByRole_Code(RoleCode.ADMIN)).isFalse();
    }
}
