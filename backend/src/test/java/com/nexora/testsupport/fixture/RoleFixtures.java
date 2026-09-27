package com.nexora.testsupport.fixture;

import java.util.EnumMap;
import java.util.Map;

import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;

public final class RoleFixtures {

    private static final Map<RoleCode, String> NAMES = names();

    private RoleFixtures() {
    }

    public static Role role(RoleCode code) {
        return new Role(code, NAMES.get(code));
    }

    private static Map<RoleCode, String> names() {
        Map<RoleCode, String> names = new EnumMap<>(RoleCode.class);
        names.put(RoleCode.REQUESTER, "Requester");
        names.put(RoleCode.TECHNICIAN, "Technician");
        names.put(RoleCode.MANAGER, "Manager");
        names.put(RoleCode.ADMIN, "Administrator");
        return Map.copyOf(names);
    }
}
