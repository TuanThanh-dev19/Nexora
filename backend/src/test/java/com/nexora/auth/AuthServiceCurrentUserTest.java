package com.nexora.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.security.JwtTokenBuilder;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserRoleId;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit test cho {@link AuthService#getCurrentUser(Jwt)}.
 *
 * <p>Kiểm tra: token hợp lệ, user không tồn tại, user bị disable/locked,
 * và bảo đảm response không lộ passwordHash.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceCurrentUserTest {

    private static final Long USER_ID = 10L;
    private static final String EMAIL = "requester@nexora.local";
    private static final String HASHED_PASSWORD = "$2a$10$hashed";

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenBuilder jwtTokenBuilder;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void getCurrentUser_with_valid_token_returns_correct_profile() {
        AppUser activeUser = buildUser(UserStatus.ACTIVE);
        when(userRepository.findByIdWithRoles(USER_ID)).thenReturn(Optional.of(activeUser));

        CurrentUserResponse response = authService.getCurrentUser(mockJwt(USER_ID));

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.roles()).containsExactly("REQUESTER");
    }

    @Test
    void getCurrentUser_does_not_expose_passwordHash() {
        when(userRepository.findByIdWithRoles(USER_ID))
                .thenReturn(Optional.of(buildUser(UserStatus.ACTIVE)));

        CurrentUserResponse response = authService.getCurrentUser(mockJwt(USER_ID));

        // Record CurrentUserResponse không được có field passwordHash / password
        assertThat(response.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("passwordHash", "password");
    }

    // -------------------------------------------------------------------------
    // User không tồn tại trong DB
    // -------------------------------------------------------------------------

    @Test
    void getCurrentUser_throws_when_user_not_found_in_db() {
        when(userRepository.findByIdWithRoles(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(mockJwt(USER_ID)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -------------------------------------------------------------------------
    // User bị disable sau khi token được phát hành
    // -------------------------------------------------------------------------

    @Test
    void getCurrentUser_throws_when_user_is_disabled_after_token_issued() {
        AppUser disabledUser = buildUser(UserStatus.DISABLED);
        when(userRepository.findByIdWithRoles(USER_ID)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> authService.getCurrentUser(mockJwt(USER_ID)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getCurrentUser_throws_when_user_is_locked() {
        AppUser lockedUser = buildUser(UserStatus.LOCKED);
        when(userRepository.findByIdWithRoles(USER_ID)).thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> authService.getCurrentUser(mockJwt(USER_ID)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad", "0", "-1", "+1", "1.5", " 10", "10 ", "9223372036854775808"})
    void malformedSubjectIsRejectedBeforeReadingUsers(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        assertThatThrownBy(() -> authService.getCurrentUser(jwt))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid identity.");
        verifyNoInteractions(userRepository, passwordEncoder, jwtTokenBuilder);
    }

    @Test
    void absentJwtIsRejectedBeforeReadingUsers() {
        assertThatThrownBy(() -> authService.getCurrentUser(null)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void maximumLongSubjectIsReadWithoutOverflow() {
        when(userRepository.findByIdWithRoles(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.getCurrentUser(mockJwt(Long.MAX_VALUE)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Tạo mock {@link Jwt} với {@code sub = userId}.
     */
    private Jwt mockJwt(Long userId) {
        return new Jwt(
                "mock.jwt.token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", String.valueOf(userId),
                        "roles", List.of("REQUESTER")
                )
        );
    }

    /**
     * Tạo AppUser với role REQUESTER bằng reflection.
     */
    private AppUser buildUser(UserStatus status) {
        AppUser user = new AppUser(EMAIL, HASHED_PASSWORD, "Nguyễn Văn A", status);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Role role = new Role(RoleCode.REQUESTER, "Requester");
        ReflectionTestUtils.setField(role, "id", (short) 1);

        UserRoleId userRoleId = new UserRoleId(USER_ID, (short) 1);
        UserRole userRole = new UserRole(user, role, null);
        ReflectionTestUtils.setField(userRole, "id", userRoleId);

        @SuppressWarnings("unchecked")
        java.util.List<UserRole> assignments =
                (java.util.List<UserRole>) ReflectionTestUtils.getField(user, "roleAssignments");
        assignments.add(userRole);

        return user;
    }
}
