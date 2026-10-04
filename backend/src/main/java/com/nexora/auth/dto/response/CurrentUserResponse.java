package com.nexora.auth.dto.response;

import java.util.List;

/**
 * Thông tin người dùng hiện tại, trả về bởi GET /api/v1/auth/me.
 *
 * <p>Không bao giờ chứa {@code passwordHash} hoặc dữ liệu nhạy cảm.
 * {@code status} phản ánh trạng thái tài khoản tại thời điểm gọi API
 * (quyền dựa trên DB, không phải JWT cũ).
 */
public record CurrentUserResponse(
    Long id,
    String email,
    String fullName,
    String status,
    List<String> roles
) {}
