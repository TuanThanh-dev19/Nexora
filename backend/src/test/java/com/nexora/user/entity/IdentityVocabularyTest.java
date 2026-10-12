package com.nexora.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class IdentityVocabularyTest {

    @Test
    void exposesOnlyCanonicalUserStatuses() {
        assertThat(UserStatus.values()).containsExactly(
                UserStatus.ACTIVE,
                UserStatus.LOCKED,
                UserStatus.DISABLED);
    }

    @Test
    void exposesOnlyCanonicalRoleCodes() {
        assertThat(RoleCode.values()).containsExactly(
                RoleCode.REQUESTER,
                RoleCode.TECHNICIAN,
                RoleCode.MANAGER,
                RoleCode.ADMIN);
        assertThat(RoleCode.values())
                .extracting(Enum::name)
                .doesNotContain("USER");
    }

    @Test
    void userRoleIdUsesBothForeignKeysForIdentity() {
        UserRoleId first = new UserRoleId(10L, (short) 2);
        UserRoleId same = new UserRoleId(10L, (short) 2);
        UserRoleId differentRole = new UserRoleId(10L, (short) 3);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(differentRole);
    }

    @Test
    void doesNotSerializePasswordHash() throws JsonProcessingException {
        AppUser user = new AppUser(
                "serialization@example.test", "must-not-be-serialized", "Serialization Test");

        String json = new ObjectMapper().writeValueAsString(user);

        assertThat(json)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("must-not-be-serialized");
    }
}
