package com.nexora.auth.dto.response;

import java.util.List;

/**
 * Safe user data returned by GET /api/v1/me (future milestone).
 * Does NOT expose passwordHash or JPA entity fields.
 */
public record CurrentUserResponse(
    Long id,
    String email,
    String displayName,
    List<String> roles
) {}
