package com.nexora.auth.dto.response;

import java.util.List;

/**
 * Response body cho POST /api/v1/auth/login.
 *
 * <p>Không bao giờ chứa {@code passwordHash} hoặc dữ liệu nhạy cảm.
 * {@code expiresIn} tính bằng giây, khớp với {@code JwtTokenBuilder.TOKEN_TTL_SECONDS}.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {

    /**
     * Tóm tắt thông tin user an toàn để trả về trong response đăng nhập.
     *
     * @param id       primary key của user
     * @param email    email gốc (không normalize) của user
     * @param fullName họ tên đầy đủ
     * @param roles    danh sách RoleCode string (e.g. ["REQUESTER"])
     */
    public record UserSummary(
            Long id,
            String email,
            String fullName,
            List<String> roles
    ) {}
}
