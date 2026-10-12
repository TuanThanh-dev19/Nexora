package com.nexora.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Builds and signs HS256 JWT tokens following the approved claims contract:
 * <ul>
 *   <li>{@code sub} — user ID (as string)</li>
 *   <li>{@code roles} — list of canonical RoleCode strings</li>
 *   <li>{@code iat} / {@code exp} — issued-at and expiry (8 hours)</li>
 * </ul>
 */
@Component
public class JwtTokenBuilder {

    public static final long TOKEN_TTL_SECONDS = 8 * 60 * 60; // 8 hours

    private final JwtEncoder encoder;

    public JwtTokenBuilder(@Value("${app.security.jwt-secret}") String jwtSecret) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /**
     * Creates a signed JWT for the given user.
     *
     * @param userId    the user's database ID
     * @param roleCodes canonical role codes (e.g. ["REQUESTER"])
     * @return compact JWT string
     */
    public String buildToken(Long userId, List<String> roleCodes) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roleCodes)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TOKEN_TTL_SECONDS))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
