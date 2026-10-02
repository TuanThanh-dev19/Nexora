package com.nexora.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByNormalizedEmail(String normalizedEmail);

    /**
     * Tìm user theo normalized email và eager-load toàn bộ role assignments trong
     * một câu query duy nhất (tránh N+1 khi login).
     */
    @Query("""
            select distinct u
            from AppUser u
            left join fetch u.roleAssignments ra
            left join fetch ra.role
            where u.normalizedEmail = :normalizedEmail
            """)
    Optional<AppUser> findByNormalizedEmailWithRoles(@Param("normalizedEmail") String normalizedEmail);

    @Query("""
            select distinct user
            from AppUser user
            left join fetch user.roleAssignments assignment
            left join fetch assignment.role
            where user.id = :id
            """)
    Optional<AppUser> findByIdWithRoles(@Param("id") Long id);
}
