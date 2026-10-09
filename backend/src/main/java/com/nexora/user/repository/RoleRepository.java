package com.nexora.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;

import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByCode(RoleCode code);

    List<Role> findByCodeIn(Collection<RoleCode> codes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from Role role where role.code = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") RoleCode code);
}

