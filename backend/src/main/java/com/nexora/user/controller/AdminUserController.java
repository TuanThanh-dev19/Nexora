package com.nexora.user.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.common.pagination.PageResponse;
import com.nexora.user.dto.UserCreateRequest;
import com.nexora.user.dto.UserListQuery;
import com.nexora.user.dto.UserResponse;
import com.nexora.user.dto.UserRolesUpdateRequest;
import com.nexora.user.dto.UserStatusUpdateRequest;
import com.nexora.user.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Administration", description = "Admin user and role management APIs")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List users with pagination and filtering")
    public PageResponse<UserResponse> list(@RequestParam MultiValueMap<String, String> queryParams) {
        UserListQuery query = UserListQuery.from(queryParams);
        return adminUserService.listUsers(query);
    }

    @PostMapping
    @Operation(summary = "Create a new user")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        UserResponse created = adminUserService.createUser(request, adminId);
        URI locationUri = URI.create("/api/v1/admin/users/" + created.id());
        return ResponseEntity.created(locationUri).body(created);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user details by ID")
    public UserResponse getById(@PathVariable Long userId) {
        return adminUserService.getUserById(userId);
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Update user status (ACTIVE, LOCKED, DISABLED)")
    public UserResponse updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return adminUserService.updateUserStatus(userId, request);
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "Update user role assignments")
    public UserResponse updateRoles(
            @PathVariable Long userId,
            @Valid @RequestBody UserRolesUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Long adminId = jwt != null ? Long.valueOf(jwt.getSubject()) : null;
        return adminUserService.updateUserRoles(userId, request, adminId);
    }
}
