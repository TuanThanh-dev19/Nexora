package com.nexora.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.auth.dto.request.LoginRequest;
import com.nexora.auth.dto.response.LoginResponse;
import com.nexora.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoint công khai để đăng nhập và nhận JWT.
 *
 * <p>Đây là endpoint public duy nhất được thêm trong issue này.
 * Tất cả các API nghiệp vụ khác vẫn yêu cầu JWT hợp lệ.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Đăng nhập và phát hành JWT")
@SecurityRequirements   // Override global bearerAuth — endpoint này không cần token
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Xác thực email/mật khẩu và phát hành JWT HS256 có hạn 8 giờ.
     *
     * <p>Email được normalize tự động (trim + uppercase) trước khi tìm kiếm.
     * Mọi lỗi xác thực đều trả {@code 401 Unauthorized} với {@code ProblemDetail}
     * không tiết lộ nguyên nhân cụ thể.
     *
     * @param request body chứa {@code email} và {@code password}
     * @return {@code 200 OK} với {@link LoginResponse} chứa JWT và thông tin user
     */
    @Operation(
            summary = "Đăng nhập",
            description = "Xác thực email/mật khẩu BCrypt, trả JWT HS256 có thời hạn 8 giờ. " +
                    "Email được normalize (trim + uppercase) tự động. " +
                    "Mọi lỗi xác thực đều trả 401 mà không lộ nguyên nhân cụ thể."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Đăng nhập thành công — trả accessToken và thông tin user",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request không hợp lệ (thiếu field hoặc email sai định dạng)",
                    content = @Content(schema = @Schema())
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Sai thông tin đăng nhập hoặc tài khoản không hoạt động",
                    content = @Content(schema = @Schema())
            )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
