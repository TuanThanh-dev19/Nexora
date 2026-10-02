package com.nexora.auth;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nexora.auth.security.JwtTokenBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenBuilderTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-ok!";
    private final JwtTokenBuilder builder = new JwtTokenBuilder(SECRET);

    @Test
    void token_contains_correct_subject() {
        String token = builder.buildToken(42L, List.of("REQUESTER"));
        Jwt decoded = decodeToken(token, SECRET);
        assertThat(decoded.getSubject()).isEqualTo("42");
    }

    @Test
    void token_contains_roles_claim() {
        String token = builder.buildToken(1L, List.of("MANAGER", "ADMIN"));
        Jwt decoded = decodeToken(token, SECRET);
        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("MANAGER", "ADMIN");
    }

    @Test
    void token_expires_in_eight_hours() {
        String token = builder.buildToken(1L, List.of("TECHNICIAN"));
        Jwt decoded = decodeToken(token, SECRET);
        long ttlSeconds = decoded.getExpiresAt().getEpochSecond()
                - decoded.getIssuedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(JwtTokenBuilder.TOKEN_TTL_SECONDS);
    }

    @Test
    void tampered_signature_is_rejected() {
        String token = builder.buildToken(99L, List.of("ADMIN"));

        // Sửa phần signature (phần thứ 3 sau dấu chấm)
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignatureXXXXXX";

        // Decoder phải ném exception khi chữ ký không khớp
        assertThatThrownBy(() -> decodeToken(tamperedToken, SECRET))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    void token_signed_with_different_secret_is_rejected() {
        String token = builder.buildToken(1L, List.of("REQUESTER"));
        String wrongSecret = "completely-different-secret-32bytes!!";

        assertThatThrownBy(() -> decodeToken(token, wrongSecret))
                .isInstanceOf(BadJwtException.class);
    }

    private Jwt decodeToken(String token, String secret) {
        byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(bytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build()
                .decode(token);
    }
}
