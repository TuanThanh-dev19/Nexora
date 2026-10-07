package com.nexora.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.category.entity.IncidentCategory;

/**
 * Spring Data repository for {@link IncidentCategory}.
 *
 * <p>All status-changing operations (activate / deactivate) must go through a
 * dedicated transactional service — do not use {@link #save} to change
 * {@code active} directly in production code.
 */
public interface IncidentCategoryRepository extends JpaRepository<IncidentCategory, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from IncidentCategory c where c.id = :id")
    Optional<IncidentCategory> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<IncidentCategory> findAllByOrderByNameAscIdAsc();

    List<IncidentCategory> findByActiveTrueOrderByNameAscIdAsc();

    /**
     * Returns all categories that are currently active, ordered by display name.
     *
     * <p>Mirrors the {@code idx_incident_categories_active_name} DB index.
     *
     * @return active categories sorted alphabetically by name
     */
    List<IncidentCategory> findByActiveTrueOrderByName();

    /**
     * Looks up a category by its unique machine-readable code.
     *
     * @param code the unique category code (e.g. "PROJECTOR_FAULT")
     * @return the category wrapped in an {@link Optional}, or empty if not found
     */
    Optional<IncidentCategory> findByCode(String code);
}
