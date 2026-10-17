package com.nexora.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);

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

    @Query("""
            select distinct user
            from AppUser user
            left join fetch user.roleAssignments assignment
            left join fetch assignment.role
            where user.id in :ids
            """)
    List<AppUser> findAllByIdWithRoles(@Param("ids") Collection<Long> ids);

    @Query("""
            select distinct u
            from AppUser u
            left join fetch u.roleAssignments ra
            left join fetch ra.role
            where exists (
                select 1 from UserRole ur
                where ur.user = u and ur.role.code = com.nexora.user.entity.RoleCode.TECHNICIAN
            )
            or exists (
                select 1 from TechnicianProfile tp
                where tp.technicianId = u.id
            )
            order by u.fullName asc, u.id asc
            """)
    List<AppUser> findAllCandidateUsersWithRoles();

    @Query("""
            select distinct u
            from AppUser u
            left join fetch u.roleAssignments ra
            left join fetch ra.role
            where (
                exists (
                    select 1 from UserRole ur
                    where ur.user = u and ur.role.code = com.nexora.user.entity.RoleCode.TECHNICIAN
                )
                or exists (
                    select 1 from TechnicianProfile tp
                    where tp.technicianId = u.id
                )
            )
            and (
                lower(u.fullName) like lower(concat('%', :query, '%'))
                or lower(u.email) like lower(concat('%', :query, '%'))
            )
            order by u.fullName asc, u.id asc
            """)
    List<AppUser> findCandidateUsersWithRolesMatching(@Param("query") String query);
}

