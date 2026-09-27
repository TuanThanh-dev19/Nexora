package com.nexora.testsupport.fixture;

import java.util.Locale;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserStatus;

public final class UserFixtures {

    public static final String TEST_ONLY_PASSWORD_HASH = "test-only-non-production-password-hash";

    private UserFixtures() {
    }

    public static AppUser requester() {
        return userFor(RoleCode.REQUESTER);
    }

    public static AppUser technician() {
        return userFor(RoleCode.TECHNICIAN);
    }

    public static AppUser manager() {
        return userFor(RoleCode.MANAGER);
    }

    public static AppUser admin() {
        return userFor(RoleCode.ADMIN);
    }

    public static AppUser userFor(RoleCode roleCode) {
        String roleName = roleCode.name().toLowerCase(Locale.ROOT);
        return builder()
                .email(roleName + "@example.test")
                .fullName("Test " + displayName(roleCode))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String displayName(RoleCode roleCode) {
        String value = roleCode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public static final class Builder {

        private String email = "user@example.test";
        private String passwordHash = TEST_ONLY_PASSWORD_HASH;
        private String fullName = "Test User";
        private UserStatus status = UserStatus.ACTIVE;

        private Builder() {
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public Builder status(UserStatus status) {
            this.status = status;
            return this;
        }

        public AppUser build() {
            return new AppUser(email, passwordHash, fullName, status);
        }
    }
}
