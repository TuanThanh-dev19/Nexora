package com.nexora.auth;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.auth.dto.request.LoginRequest;
import com.nexora.auth.dto.response.LoginResponse;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_SECRET = "test-secret-at-least-32-bytes-ok!";
    private static final String RAW_PASSWORD = "secure-password";
    private static final String HASHED_PASSWORD = "$2a$10$hashed";
    private static final String EMAIL = "requester@nexora.local";
    private static final String NORMALIZED_EMAIL = "REQUESTER@NEXORA.LOCAL";

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenBuilder jwtTokenBuilder;

    @InjectMocks
    private AuthService authService;

    private AppUser activeUser;

    @BeforeEach
    void setUp() {
        activeUser = buildUser(UserStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void login_with_valid_credentials_returns_login_response() {
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
        when(jwtTokenBuilder.buildToken(anyLong(), anyList())).thenReturn("jwt.token.value");

        LoginResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("jwt.token.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(JwtTokenBuilder.TOKEN_TTL_SECONDS);
        assertThat(response.user().id()).isEqualTo(activeUser.getId());
        assertThat(response.user().email()).isEqualTo(EMAIL);
        assertThat(response.user().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.user().roles()).containsExactly("REQUESTER");
    }

    @Test
    void login_normalizes_email_with_trim_and_uppercase() {
        // Truyền email có khoảng trắng và chữ thường — phải được normalize trước khi query
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenBuilder.buildToken(anyLong(), anyList())).thenReturn("token");

        authService.login(new LoginRequest("  " + EMAIL + "  ", RAW_PASSWORD));

        // Verify query được gọi với đúng normalized email
        verify(userRepository).findByNormalizedEmailWithRoles(NORMALIZED_EMAIL);
    }

    // -------------------------------------------------------------------------
    // Email không tồn tại
    // -------------------------------------------------------------------------

    @Test
    void login_throws_when_email_not_found() {
        when(userRepository.findByNormalizedEmailWithRoles(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        // BCrypt KHÔNG được gọi khi email không tồn tại
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Sai mật khẩu
    // -------------------------------------------------------------------------

    @Test
    void login_throws_when_password_does_not_match() {
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        // JWT KHÔNG được phát hành
        verify(jwtTokenBuilder, never()).buildToken(anyLong(), anyList());
    }

    // -------------------------------------------------------------------------
    // Tài khoản không ACTIVE
    // -------------------------------------------------------------------------

    @Test
    void login_throws_when_user_is_locked() {
        AppUser lockedUser = buildUser(UserStatus.LOCKED);
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(lockedUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtTokenBuilder, never()).buildToken(anyLong(), anyList());
    }

    @Test
    void login_throws_when_user_is_disabled() {
        AppUser disabledUser = buildUser(UserStatus.DISABLED);
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(disabledUser));
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtTokenBuilder, never()).buildToken(anyLong(), anyList());
    }

    // -------------------------------------------------------------------------
    // Response không chứa passwordHash
    // -------------------------------------------------------------------------

    @Test
    void login_response_does_not_expose_password_hash() {
        when(userRepository.findByNormalizedEmailWithRoles(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenBuilder.buildToken(anyLong(), anyList())).thenReturn("token");

        LoginResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        // UserSummary record không có trường passwordHash — kiểm tra class fields
        assertThat(response.user().getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("passwordHash", "password");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Tạo AppUser với role REQUESTER bằng reflection (constructor không nhận ID).
     */
    private AppUser buildUser(UserStatus status) {
        AppUser user = new AppUser(EMAIL, HASHED_PASSWORD, "Nguyễn Văn A", status);
        ReflectionTestUtils.setField(user, "id", 10L);

        Role role = new Role(RoleCode.REQUESTER, "Requester");
        ReflectionTestUtils.setField(role, "id", (short) 1);

        UserRoleId userRoleId = new UserRoleId(10L, (short) 1);
        UserRole userRole = new UserRole(user, role, null);
        ReflectionTestUtils.setField(userRole, "id", userRoleId);

        // Thêm vào danh sách roleAssignments trong user
        @SuppressWarnings("unchecked")
        java.util.List<UserRole> assignments =
                (java.util.List<UserRole>) ReflectionTestUtils.getField(user, "roleAssignments");
        assignments.add(userRole);

        return user;
    }
}
