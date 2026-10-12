package com.nexora.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/v1/public/auth/login (future milestone). */
public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}
