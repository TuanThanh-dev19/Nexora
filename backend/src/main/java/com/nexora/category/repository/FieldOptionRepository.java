package com.nexora.category.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.category.entity.FieldOption;

/**
 * Spring Data repository for {@link FieldOption}.
 *
 * <p>Use the ordered query methods to ensure deterministic option presentation.
 * The preferred method for ticket submission and form rendering is
 * {@link #findByFieldDefinitionIdAndActiveTrueOrderByDisplayOrderAsc}, which
 * excludes inactive options and preserves the configured display sequence.
 */
public interface FieldOptionRepository extends JpaRepository<FieldOption, Long> {

    /** Reads stored form options, including inactive options needed for historical tickets. */
    List<FieldOption> findByFieldDefinitionFormVersionIdOrderByDisplayOrderAscIdAsc(Long formVersionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from FieldOption o where o.fieldDefinition.formVersion.id = :id")
    void deleteForForm(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Returns only active options for the given field definition, ordered by
     * {@code display_order} ascending.
     *
     * <p>This is the preferred method for form rendering and ticket submission.
     * Mirrors the {@code idx_field_options_field_order} DB index
     * ({@code field_definition_id, display_order}).
     *
     * @param fieldDefinitionId the PK of the owning {@link com.nexora.category.entity.FieldDefinition}
     * @return active options in deterministic display order; empty if none exist
     */
    List<FieldOption> findByFieldDefinitionIdAndActiveTrueOrderByDisplayOrderAsc(Long fieldDefinitionId);

    /**
     * Returns all options (active and inactive) for the given field definition,
     * ordered by {@code display_order} ascending.
     *
     * <p>Use this method in administration contexts where inactive options must
     * also be visible (e.g. audit trails, form editing in future milestones).
     *
     * @param fieldDefinitionId the PK of the owning {@link com.nexora.category.entity.FieldDefinition}
     * @return all options in deterministic display order; empty if none exist
     */
    List<FieldOption> findByFieldDefinitionIdOrderByDisplayOrderAsc(Long fieldDefinitionId);
}
