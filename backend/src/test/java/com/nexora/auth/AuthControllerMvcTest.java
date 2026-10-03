package com.nexora.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.auth.controller.AuthController;
import com.nexora.auth.dto.request.LoginRequest;
import com.nexora.auth.dto.response.LoginResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.exception.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVC slice test cho AuthController.
 *
 * <p>Security auto-configuration bị loại trừ để tránh phụ thuộc vào SecurityConfig
 * (vốn cần @Value jwt-secret, cors, JwtDecoder...).
 * Endpoint login vốn là public nên không cần test security filter ở đây —
 * đó là trách nhiệm của SecurityConfig integration test.
 *
 * <p>GlobalExceptionHandler được import để test ProblemDetail response shape.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@Import(GlobalExceptionHandler.class)
class AuthControllerMvcTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void login_with_valid_credentials_returns_200_and_token() throws Exception {
        LoginResponse fakeResponse = new LoginResponse(
                "jwt.header.payload.signature",
                "Bearer",
                28800L,
                new LoginResponse.UserSummary(10L, "requester@nexora.local", "Nguyễn Văn A",
                        List.of("REQUESTER"))
        );
        when(authService.login(any())).thenReturn(fakeResponse);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("requester@nexora.local", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.header.payload.signature"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(28800))
                .andExpect(jsonPath("$.user.id").value(10))
                .andExpect(jsonPath("$.user.email").value("requester@nexora.local"))
                .andExpect(jsonPath("$.user.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.user.roles[0]").value("REQUESTER"))
                // Bảo đảm không có passwordHash trong response
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // Invalid credentials → 401 ProblemDetail
    // -------------------------------------------------------------------------

    @Test
    void login_with_wrong_password_returns_401_problem_detail() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("wrong@nexora.local", "bad-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_CREDENTIALS.toString()))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                // Detail không được tiết lộ nguyên nhân cụ thể
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void login_with_locked_account_returns_same_401_as_wrong_password() throws Exception {
        // LOCKED và sai mật khẩu phải trả cùng response — test tính đồng nhất
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("locked@nexora.local", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(ProblemTypes.INVALID_CREDENTIALS.toString()));
    }

    // -------------------------------------------------------------------------
    // Bean Validation → 400 Bad Request
    // -------------------------------------------------------------------------

    @Test
    void login_with_missing_email_returns_400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_with_invalid_email_format_returns_400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("not-an-email", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_with_blank_password_returns_400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("user@nexora.local", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_with_missing_body_returns_400() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
