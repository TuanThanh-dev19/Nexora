package com.nexora.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.nexora.auth.security.JwtRoleConverter;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void maps_role_codes_to_spring_authorities_with_prefix() {
        Jwt jwt = buildJwtWithRoles(List.of("REQUESTER", "MANAGER"));
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_REQUESTER", "ROLE_MANAGER");
    }

    @Test
    void returns_empty_when_roles_claim_absent() {
        Jwt jwt = buildJwtWithRoles(null);
        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertThat(authorities).isEmpty();
    }

    private Jwt buildJwtWithRoles(List<String> roles) {
        Map<String, Object> claims = roles != null
                ? Map.of("roles", roles, "sub", "1")
                : Map.of("sub", "1");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claims(c -> c.putAll(claims))
                .build();
    }
}
