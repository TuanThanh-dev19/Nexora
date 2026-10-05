package com.nexora.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserRoleId;
import com.nexora.user.entity.RoleCode;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    boolean existsByRole_Code(RoleCode code);
}
