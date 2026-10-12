package com.nexora.auth;

import com.nexora.auth.controller.AuthController;
import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.exception.ProblemTypes;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice test cho GET /api/v1/auth/me — với Security context đầy đủ.
 *
 * <p>Khác với {@link AuthControllerMvcTest} (exclude security),
 * class này giữ Security filter chain để test:
 * <ul>
 *   <li>401 khi thiếu token (Spring Security từ chối)</li>
 *   <li>200 khi JWT hợp lệ (jwt() post-processor)</li>
 *   <li>401 khi service ném {@link InvalidCredentialsException} (user bị disable)</li>
 * </ul>
 *
 * <p>{@link JwtDecoder} được mock để tránh phụ thuộc vào jwt-secret config.
 * {@link JwtRoleConverter}, {@link ProblemAuthenticationEntryPoint} và
 * {@link ProblemAccessDeniedHandler} được import để Security filter hoạt động đúng.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class AuthControllerMeMvcTest {

    private static final String ME_URL = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    /**
     * JwtDecoder mock: Spring Security dùng bean này để validate token header thật.
     * Với jwt() post-processor, Spring Security bỏ qua JwtDecoder và inject Jwt trực tiếp.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // -------------------------------------------------------------------------
    // 200 OK — token hợp lệ
    // -------------------------------------------------------------------------

    @Test
    void getMe_with_valid_jwt_returns_200_and_user_profile() throws Exception {
        CurrentUserResponse profile = new CurrentUserResponse(
                10L, "requester@nexora.local", "Nguyễn Văn A", "ACTIVE", List.of("REQUESTER"));
        when(authService.getCurrentUser(any())).thenReturn(profile);

        mockMvc.perform(get(ME_URL)
                        .with(jwt().jwt(j -> j.subject("10").claim("roles", List.of("REQUESTER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("requester@nexora.local"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("REQUESTER"))
                // Bảo đảm không có passwordHash trong response
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // 401 — thiếu token
    // -------------------------------------------------------------------------

    @Test
    void getMe_without_token_returns_401() throws Exception {
        // Không có Authorization header — Spring Security từ chối trước khi vào controller
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 401 — user bị disable sau khi token được phát hành
    // -------------------------------------------------------------------------

    @Test
    void getMe_when_user_disabled_after_token_issued_returns_401() throws Exception {
        when(authService.getCurrentUser(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(get(ME_URL)
                        .with(jwt().jwt(j -> j.subject("10"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_CREDENTIALS.toString()));
    }
}
