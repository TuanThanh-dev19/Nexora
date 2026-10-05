package com.nexora.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.RoleRepository;
import com.nexora.user.repository.UserRoleRepository;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class AdminBootstrapService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapService(AppUserRepository userRepository,
                                 RoleRepository roleRepository,
                                 UserRoleRepository userRoleRepository,
                                 PasswordEncoder passwordEncoder,
                                 Validator validator) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional
    public boolean createInitialAdmin(String email, String password) {
        String trimmedEmail = email == null ? "" : email.trim();
        BootstrapCredentials credentials = new BootstrapCredentials(trimmedEmail, password);
        if (!validator.validate(credentials).isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Admin bootstrap requires a valid ADMIN_BOOTSTRAP_EMAIL "
                    + "(up to 254 characters) and ADMIN_BOOTSTRAP_PASSWORD "
                    + "(at least 8 characters and at most 72 UTF-8 bytes).");
        }

        // Serialize bootstrap attempts across instances before checking for an Admin.
        Role adminRole = roleRepository.findByCodeForUpdate(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "Admin role is missing. Apply Flyway reference-data migrations before bootstrap."));

        if (userRoleRepository.existsByRole_Code(RoleCode.ADMIN)) {
            return false;
        }

        if (userRepository.findByNormalizedEmail(trimmedEmail.toLowerCase(Locale.ROOT)).isPresent()) {
            throw new IllegalStateException("Admin bootstrap email already belongs to an existing account. "
                    + "Choose a different ADMIN_BOOTSTRAP_EMAIL.");
        }

        AppUser admin = userRepository.save(new AppUser(
                trimmedEmail, passwordEncoder.encode(password), "Local Admin"));
        userRoleRepository.saveAndFlush(new UserRole(admin, adminRole, null));
        return true;
    }

    private record BootstrapCredentials(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8) String password) {
    }
}
