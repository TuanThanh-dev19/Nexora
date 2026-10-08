package com.nexora.auth.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.auth.dto.request.LoginRequest;
import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.dto.response.LoginResponse;
import com.nexora.auth.security.JwtTokenBuilder;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;

/**
 * Xử lý logic đăng nhập: xác thực email/mật khẩu, kiểm tra trạng thái tài khoản,
 * phát hành JWT HS256.
 *
 * <p>Tất cả lỗi đăng nhập đều ném {@link InvalidCredentialsException} với cùng thông điệp
 * để không lộ nguyên nhân cụ thể (email không tồn tại, sai mật khẩu, hay bị khoá).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenBuilder jwtTokenBuilder;

    public AuthService(AppUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenBuilder jwtTokenBuilder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenBuilder = jwtTokenBuilder;
    }

    /**
     * Xác thực thông tin đăng nhập và phát hành JWT.
     *
     * <p>Thứ tự kiểm tra:
     * <ol>
     *   <li>Tìm user theo normalized email (UPPER-TRIM)</li>
     *   <li>Xác minh mật khẩu BCrypt</li>
     *   <li>Kiểm tra trạng thái tài khoản = ACTIVE</li>
     *   <li>Phát hành JWT với sub=userId, roles=[...]</li>
     * </ol>
     *
     * @param request chứa email và password từ request body
     * @return {@link LoginResponse} chứa JWT và thông tin user (không có passwordHash)
     * @throws InvalidCredentialsException nếu thất bại ở bất kỳ bước nào
     */
    public LoginResponse login(LoginRequest request) {
        // 1. Normalize email: trim khoảng trắng + uppercase để match computed column DB
        String normalizedEmail = request.email().trim().toUpperCase();

        // 2. Tìm user với roles eager-loaded trong 1 query (tránh N+1)
        AppUser user = userRepository
                .findByNormalizedEmailWithRoles(normalizedEmail)
                .orElseThrow(() -> {
                    // Log hash của email để debug, KHÔNG log email gốc
                    log.warn("Login failed: email not found (hash={})", normalizedEmail.hashCode());
                    return new InvalidCredentialsException();
                });

        // 3. Xác minh mật khẩu BCrypt — check TRƯỚC khi check status
        //    (tránh timing attack phân biệt "email tồn tại nhưng locked" vs "không tồn tại")
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: wrong password for userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        // 4. Chỉ user ACTIVE được đăng nhập
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login rejected: userId={} status={}", user.getId(), user.getStatus());
            throw new InvalidCredentialsException();
        }

        // 5. Lấy danh sách role code (roleAssignments đã được eager-load ở bước 2)
        List<String> roleCodes = user.getRoleAssignments().stream()
                .map(ur -> ur.getRole().getCode().name())
                .toList();

        // 6. Phát hành JWT — sub=userId, roles=[...]
        String token = jwtTokenBuilder.buildToken(user.getId(), roleCodes);

        // Log thành công: KHÔNG log token đầy đủ
        log.info("Login successful: userId={} roles={}", user.getId(), roleCodes);

        // 7. Build response — email gốc (không normalize), KHÔNG có passwordHash
        return new LoginResponse(
                token,
                "Bearer",
                JwtTokenBuilder.TOKEN_TTL_SECONDS,
                new LoginResponse.UserSummary(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        roleCodes
                )
        );
    }

    /**
     * Tải lại thông tin người dùng hiện tại từ database theo userId trong JWT.
     *
     * <p>Luôn đọc từ DB để đảm bảo quyền phản ánh trạng thái thực tế,
     * không dựa vào claims cũ trong JWT.
     *
     * <p>Thứ tự kiểm tra:
     * <ol>
     *   <li>Parse userId từ {@code sub} claim</li>
     *   <li>Load user + roles từ DB trong 1 query (tránh N+1)</li>
     *   <li>Kiểm tra user vẫn tồn tại và {@code status = ACTIVE}</li>
     *   <li>Build và trả {@link CurrentUserResponse}</li>
     * </ol>
     *
     * @param jwt JWT đã được Spring Security xác thực
     * @return {@link CurrentUserResponse} chứa thông tin user và roles từ DB
     * @throws BadCredentialsException nếu subject không phải user ID hợp lệ
     * @throws InvalidCredentialsException nếu user không tồn tại hoặc không còn ACTIVE
     */
    public CurrentUserResponse getCurrentUser(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        if (subject == null || !subject.matches("[0-9]+")) {
            throw new BadCredentialsException("Invalid identity.");
        }
        long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException exception) {
            throw new BadCredentialsException("Invalid identity.");
        }
        if (userId <= 0) throw new BadCredentialsException("Invalid identity.");

        AppUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> {
                    log.warn("GET /auth/me: userId={} not found in DB", userId);
                    return new InvalidCredentialsException();
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("GET /auth/me: userId={} has status={}, rejecting", userId, user.getStatus());
            throw new InvalidCredentialsException();
        }

        List<String> roleCodes = user.getRoleAssignments().stream()
                .map(ur -> ur.getRole().getCode().name())
                .toList();

        log.debug("GET /auth/me: userId={} resolved successfully", userId);

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                roleCodes
        );
    }
}
