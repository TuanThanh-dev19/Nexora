package com.nexora.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    boolean existsByRole_Code(RoleCode code);

    List<UserRole> findByUser_Id(Long userId);

    @Modifying
    @Query("delete from UserRole ur where ur.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}

