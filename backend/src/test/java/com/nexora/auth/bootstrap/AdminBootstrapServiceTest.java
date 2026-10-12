package com.nexora.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static final String EMAIL = "admin@example.test";
    private static final String PASSWORD = "test-only-password";
    private static ValidatorFactory validatorFactory;

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminBootstrapService service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new AdminBootstrapService(userRepository, roleRepository, userRoleRepository,
                passwordEncoder, validatorFactory.getValidator());
    }

    @Test
    void createsActiveAdminWithBcryptHashAndRoleAssignment() {
        Role adminRole = adminRole();
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 42L);
            return user;
        });

        assertThat(service.createInitialAdmin("  " + EMAIL + "  ", PASSWORD)).isTrue();

        ArgumentCaptor<UserRole> assignment = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).saveAndFlush(assignment.capture());
        AppUser user = assignment.getValue().getUser();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(assignment.getValue().getRole()).isSameAs(adminRole);
        assertThat(assignment.getValue().getAssignedBy()).isNull();
        verify(userRepository).findByNormalizedEmail(EMAIL);
    }

    @Test
    void skipsExistingAdminWithoutUpdatingAnyAccount() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRoleRepository.existsByRole_Code(RoleCode.ADMIN)).thenReturn(true);

        assertThat(service.createInitialAdmin(EMAIL, PASSWORD)).isFalse();

        verifyNoInteractions(userRepository);
        verify(userRoleRepository, never()).saveAndFlush(any(UserRole.class));
    }

    @Test
    void rejectsExistingNonAdminEmailInsteadOfPromotingIt() {
        when(roleRepository.findByCodeForUpdate(RoleCode.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRepository.findByNormalizedEmail(EMAIL))
                .thenReturn(Optional.of(new AppUser(EMAIL, "test-only-hash", "Existing User")));

        assertThatThrownBy(() -> service.createInitialAdmin(" ADMIN@EXAMPLE.TEST ", PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belongs to an existing account")
                .hasMessageNotContaining(PASSWORD);

        verify(userRepository, never()).save(any(AppUser.class));
        verify(userRoleRepository, never()).saveAndFlush(any(UserRole.class));
    }

    @Test
    void reportsMissingSeededRoleWithoutCreatingAUser() {
        assertThatThrownBy(() -> service.createInitialAdmin(EMAIL, PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Apply Flyway reference-data migrations");

        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,test-only-password",
            "'',test-only-password",
            "invalid-email,test-only-password",
            "admin@example.test,NULL",
            "admin@example.test,''",
            "admin@example.test,short",
            "admin@example.test,'        '"
    }, nullValues = "NULL")
    void rejectsMissingOrInvalidCredentialsBeforeDatabaseAccess(String email, String password) {
        assertThatThrownBy(() -> service.createInitialAdmin(email, password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL")
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");

        verifyNoInteractions(userRepository, roleRepository, userRoleRepository);
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimitWithoutExposingThem() {
        String password = "\u00e9".repeat(37);
        assertThatThrownBy(() -> service.createInitialAdmin(EMAIL, password))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 72 UTF-8 bytes")
                .hasMessageNotContaining(password);

        verifyNoInteractions(userRepository, roleRepository, userRoleRepository);
    }

    private Role adminRole() {
        Role role = new Role(RoleCode.ADMIN, "Admin");
        ReflectionTestUtils.setField(role, "id", (short) 4);
        return role;
    }
}
