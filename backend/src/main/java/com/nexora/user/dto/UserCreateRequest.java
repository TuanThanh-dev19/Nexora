package com.nexora.user.dto;

import java.util.Set;

import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be valid")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters")
        String password,

        @NotBlank(message = "fullName must not be blank")
        @Size(max = 120, message = "fullName must not exceed 120 characters")
        String fullName,

        UserStatus status,

        @NotEmpty(message = "roles must not be empty")
        Set<RoleCode> roles
) {
    public UserStatus resolvedStatus() {
        return status != null ? status : UserStatus.ACTIVE;
    }
}
