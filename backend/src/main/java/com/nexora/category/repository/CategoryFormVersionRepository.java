package com.nexora.category.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.FormStatus;

/**
 * Spring Data repository for {@link CategoryFormVersion}.
 *
 * <p>At most one form version per category may have status
 * {@link FormStatus#PUBLISHED} at any point in time. This invariant is
 * enforced at the database level by a filtered unique index
 * ({@code uq_category_form_versions_one_published}).
 * Use {@link #findByCategoryIdAndFormStatus} with {@link FormStatus#PUBLISHED}
 * to retrieve the current active form for a category.
 */
public interface CategoryFormVersionRepository extends JpaRepository<CategoryFormVersion, Long> {

    @org.springframework.data.jpa.repository.Query("select f.category.id from CategoryFormVersion f where f.id = :id")
    Optional<Long> findCategoryId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select coalesce(max(f.versionNumber), 0) from CategoryFormVersion f where f.category.id = :id")
    int maximumVersion(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Returns the single form version of the given category that matches the
     * given status, or empty if none exists.
     *
     * <p>Calling this with {@link FormStatus#PUBLISHED} reliably returns the
     * one currently published form because the DB filtered unique index prevents
     * more than one PUBLISHED version per category.
     *
     * @param categoryId the PK of the owning {@link com.nexora.category.entity.IncidentCategory}
     * @param formStatus the lifecycle state to filter by
     * @return the matching form version, or empty
     */
    Optional<CategoryFormVersion> findByCategoryIdAndFormStatus(Long categoryId, FormStatus formStatus);

    /**
     * Returns all form versions for the given category ordered by
     * {@code version_number} ascending.
     *
     * @param categoryId the PK of the owning category
     * @return ordered list of form versions; empty if the category has none
     */
    List<CategoryFormVersion> findByCategoryIdOrderByVersionNumberAsc(Long categoryId);
}
