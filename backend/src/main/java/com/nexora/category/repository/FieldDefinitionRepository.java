package com.nexora.category.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.category.entity.FieldDefinition;

/**
 * Spring Data repository for {@link FieldDefinition}.
 *
 * <p>Always retrieve fields through the ordered query methods to guarantee
 * deterministic presentation order. Unordered retrieval is intentionally
 * not exposed as a named query in this repository.
 */
public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {

    /**
     * Returns all field definitions belonging to the given form version,
     * ordered by {@code display_order} ascending.
     *
     * <p>Mirrors the {@code idx_field_definitions_form_order} DB index
     * ({@code form_version_id, display_order}).
     *
     * @param formVersionId the PK of the owning {@link com.nexora.category.entity.CategoryFormVersion}
     * @return field definitions in deterministic display order; empty if none exist
     */
    List<FieldDefinition> findByFormVersionIdOrderByDisplayOrderAsc(Long formVersionId);
}
