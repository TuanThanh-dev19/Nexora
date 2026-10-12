package com.nexora.testsupport.fixture;

import java.util.Arrays;

import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserFixturesTest {

    @Test
    void providesOneSafeDeterministicUserForEveryCanonicalRole() {
        assertThat(Arrays.stream(RoleCode.values()).map(UserFixtures::userFor))
                .extracting(AppUser::getEmail)
                .containsExactly(
                        "requester@example.test",
                        "technician@example.test",
                        "manager@example.test",
                        "admin@example.test");

        assertThat(Arrays.stream(RoleCode.values()).map(UserFixtures::userFor))
                .allSatisfy(user -> {
                    assertThat(user.getEmail()).endsWith("@example.test");
                    assertThat(user.getPasswordHash()).isEqualTo(UserFixtures.TEST_ONLY_PASSWORD_HASH);
                });
    }

    @Test
    void buildersReturnIndependentObjectsWithoutSharedMutableState() {
        AppUser first = UserFixtures.requester();
        AppUser second = UserFixtures.requester();

        first.changeFullName("Changed in one test");

        assertThat(first).isNotSameAs(second);
        assertThat(second.getFullName()).isEqualTo("Test Requester");
    }
}
