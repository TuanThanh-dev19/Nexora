package com.nexora.auth;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nexora.auth.security.JwtTokenBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenBuilderTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-ok!";
    private final JwtTokenBuilder builder = new JwtTokenBuilder(SECRET);

    @Test
    void token_contains_correct_subject() {
        String token = builder.buildToken(42L, List.of("REQUESTER"));
        Jwt decoded = decodeToken(token);
        assertThat(decoded.getSubject()).isEqualTo("42");
    }

    @Test
    void token_contains_roles_claim() {
        String token = builder.buildToken(1L, List.of("MANAGER", "ADMIN"));
        Jwt decoded = decodeToken(token);
        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("MANAGER", "ADMIN");
    }

    @Test
    void token_expires_in_eight_hours() {
        String token = builder.buildToken(1L, List.of("TECHNICIAN"));
        Jwt decoded = decodeToken(token);
        long ttlSeconds = decoded.getExpiresAt().getEpochSecond()
                - decoded.getIssuedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(JwtTokenBuilder.TOKEN_TTL_SECONDS);
    }

    private Jwt decodeToken(String token) {
        byte[] bytes = SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(bytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build()
                .decode(token);
    }
}
