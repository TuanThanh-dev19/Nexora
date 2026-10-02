package com.nexora.auth.service;

/**
 * Ném khi xác thực thất bại vì bất kỳ lý do nào:
 * email không tồn tại, mật khẩu sai, hoặc tài khoản không ở trạng thái ACTIVE.
 *
 * <p>Dùng chung một exception duy nhất để không lộ nguyên nhân cụ thể
 * (security best practice — không phân biệt "email không tìm thấy" vs "sai mật khẩu").
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
