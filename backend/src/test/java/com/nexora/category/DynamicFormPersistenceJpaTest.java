package com.nexora.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.nexora.category.entity.AssetPolicy;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.entity.FieldDefinition;
import com.nexora.category.entity.FieldOption;
import com.nexora.category.entity.FormStatus;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.category.entity.IssuePriority;
import com.nexora.category.repository.CategoryFormVersionRepository;
import com.nexora.category.repository.FieldDefinitionRepository;
import com.nexora.category.repository.FieldOptionRepository;
import com.nexora.category.repository.IncidentCategoryRepository;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.sla.repository.SlaPolicyRepository;
import com.nexora.testsupport.NexoraJpaTest;

/**
 * Persistence tests for the dynamic-form foundation entities and repositories.
 *
 * <p>Tests run against an in-memory H2 database in SQL Server compatibility mode
 * via {@link NexoraJpaTest} — no Docker or external infrastructure required.
 *
 * <p><strong>Coverage mapping to Acceptance Criteria:</strong>
 * <ul>
 *   <li>AC: Category/form relationships → {@link #categoryWithPublishedFormCanBeLoadedThroughRepositories}</li>
 *   <li>AC: Ordered fields              → {@link #fieldsAreReturnedInDeterministicDisplayOrder}</li>
 *   <li>AC: Ordered options             → {@link #selectFieldOptionsAreReturnedInDeterministicDisplayOrder}</li>
 *   <li>AC: Active filtering            → {@link #activeCategoryFilteringExcludesInactiveCategories}</li>
 *   <li>AC: Published-form lookup       → {@link #publishedFormLookupReturnsSinglePublishedVersion}</li>
 *   <li>AC: validation_rules JSON       → {@link #validationRulesPreservedAsRawJsonString}</li>
 *   <li>AC: DRAFT publishedAt null      → {@link #draftFormVersionHasNullPublishedAt}</li>
 *   <li>AC: Unique category code        → {@link #uniqueCodePerCategoryIsEnforced}</li>
 *   <li>AC: Unique field key per form   → {@link #uniqueFieldKeyPerFormVersionIsEnforced}</li>
 *   <li>AC: Cross-module ID refs        → {@link #crossModuleIdReferencesArePreservedAsLong}</li>
 * </ul>
 */
@NexoraJpaTest
class DynamicFormPersistenceJpaTest {

    @Autowired
    private IncidentCategoryRepository categoryRepo;

    @Autowired
    private CategoryFormVersionRepository formVersionRepo;

    @Autowired
    private FieldDefinitionRepository fieldDefRepo;

    @Autowired
    private FieldOptionRepository fieldOptionRepo;

    @Autowired
    private SlaPolicyRepository slaPolicyRepo;

    @Autowired
    private TestEntityManager entityManager;

    // =========================================================================
    // Helper factories
    // =========================================================================

    private IncidentCategory savedCategory(String code, String name) {
        IncidentCategory cat = new IncidentCategory(code, name);
        return entityManager.persistAndFlush(cat);
    }

    private IncidentCategory savedCategory(String code) {
        return savedCategory(code, "Category " + code);
    }

    private CategoryFormVersion savedFormVersion(IncidentCategory category,
                                                  int versionNumber,
                                                  FormStatus status) {
        CategoryFormVersion form = new CategoryFormVersion(category, versionNumber);
        form.changeFormStatus(status);
        if (status == FormStatus.PUBLISHED || status == FormStatus.ARCHIVED) {
            form.changePublishingMetadata(1L, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return entityManager.persistAndFlush(form);
    }

    private FieldDefinition savedField(CategoryFormVersion form, String key, int displayOrder) {
        FieldDefinition field = new FieldDefinition(
                form, key, "Label for " + key, DynamicFieldType.TEXT, displayOrder);
        return entityManager.persistAndFlush(field);
    }

    private FieldDefinition savedSelectField(CategoryFormVersion form, String key, int displayOrder) {
        FieldDefinition field = new FieldDefinition(
                form, key, "Label for " + key, DynamicFieldType.SELECT, displayOrder);
        return entityManager.persistAndFlush(field);
    }

    private FieldOption savedOption(FieldDefinition field, String value, int displayOrder) {
        FieldOption option = new FieldOption(field, value, "Label " + value, displayOrder);
        return entityManager.persistAndFlush(option);
    }

    private SlaPolicy savedSlaPolicy(String name, IssuePriority priority, int minutes) {
        SlaPolicy policy = new SlaPolicy(name, priority, minutes);
        return entityManager.persistAndFlush(policy);
    }

    // =========================================================================
    // Tests — Category / Form relationships
    // =========================================================================

    /**
     * AC: Category/form relationships — a category with a published form can be
     * loaded through repositories with all fields preserved.
     */
    @Test
    void categoryWithPublishedFormCanBeLoadedThroughRepositories() {
        IncidentCategory category = savedCategory("PROJECTOR_FAULT", "Projector Fault");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.PUBLISHED);
        entityManager.clear();

        Optional<IncidentCategory> loadedCategory = categoryRepo.findByCode("PROJECTOR_FAULT");
        assertThat(loadedCategory).isPresent();
        assertThat(loadedCategory.get().getName()).isEqualTo("Projector Fault");
        assertThat(loadedCategory.get().isActive()).isTrue();

        Optional<CategoryFormVersion> loadedForm =
                formVersionRepo.findByCategoryIdAndFormStatus(category.getId(), FormStatus.PUBLISHED);
        assertThat(loadedForm).isPresent();
        assertThat(loadedForm.get().getVersionNumber()).isEqualTo(1);
        assertThat(loadedForm.get().getFormStatus()).isEqualTo(FormStatus.PUBLISHED);
        assertThat(loadedForm.get().getPublishedAt()).isNotNull();
        assertThat(loadedForm.get().getCategory().getId()).isEqualTo(category.getId());
    }

    // =========================================================================
    // Tests — Ordered fields
    // =========================================================================

    /**
     * AC: Fields are returned in deterministic display_order — even when persisted
     * in a different order.
     */
    @Test
    void fieldsAreReturnedInDeterministicDisplayOrder() {
        IncidentCategory category = savedCategory("NETWORK_ISSUE");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);

        // Persist in reverse order to prove the repository sorts by display_order
        savedField(form, "field_c", 2);
        savedField(form, "field_a", 0);
        savedField(form, "field_b", 1);
        entityManager.clear();

        List<FieldDefinition> fields =
                fieldDefRepo.findByFormVersionIdOrderByDisplayOrderAsc(form.getId());

        assertThat(fields).hasSize(3);
        assertThat(fields.get(0).getFieldKey()).isEqualTo("field_a");
        assertThat(fields.get(1).getFieldKey()).isEqualTo("field_b");
        assertThat(fields.get(2).getFieldKey()).isEqualTo("field_c");
    }

    /**
     * AC: Multiple fields — verifies exactly three fields are linked and retrievable.
     */
    @Test
    void multipleFieldsAreLinkedToFormVersion() {
        IncidentCategory category = savedCategory("AC_FAULT");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);

        savedField(form, "description", 0);
        savedField(form, "location_detail", 1);
        savedField(form, "urgency_note", 2);
        entityManager.clear();

        List<FieldDefinition> fields =
                fieldDefRepo.findByFormVersionIdOrderByDisplayOrderAsc(form.getId());
        assertThat(fields).hasSize(3);
    }

    // =========================================================================
    // Tests — Ordered options (SELECT fields)
    // =========================================================================

    /**
     * AC: Options are returned in deterministic display_order — even when persisted
     * in a different order. Only active options are returned by the preferred query.
     */
    @Test
    void selectFieldOptionsAreReturnedInDeterministicDisplayOrder() {
        IncidentCategory category = savedCategory("ELEVATOR_ISSUE");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);
        FieldDefinition field = savedSelectField(form, "severity", 0);

        // Persist out of order
        savedOption(field, "critical", 2);
        savedOption(field, "low", 0);
        savedOption(field, "medium", 1);
        entityManager.clear();

        List<FieldOption> options =
                fieldOptionRepo.findByFieldDefinitionIdAndActiveTrueOrderByDisplayOrderAsc(field.getId());

        assertThat(options).hasSize(3);
        assertThat(options.get(0).getOptionValue()).isEqualTo("low");
        assertThat(options.get(1).getOptionValue()).isEqualTo("medium");
        assertThat(options.get(2).getOptionValue()).isEqualTo("critical");
    }

    /**
     * AC: Inactive options are excluded by the active-only query but appear
     * in the all-inclusive query.
     */
    @Test
    void inactiveOptionsAreExcludedFromActiveQuery() {
        IncidentCategory category = savedCategory("LIGHTING_ISSUE");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);
        FieldDefinition field = savedSelectField(form, "area", 0);

        FieldOption activeOption = savedOption(field, "corridor", 0);
        FieldOption inactiveOption = new FieldOption(field, "deprecated_zone", "Deprecated Zone", 1);
        inactiveOption.changeActive(false);
        entityManager.persistAndFlush(inactiveOption);
        entityManager.clear();

        List<FieldOption> activeOnly =
                fieldOptionRepo.findByFieldDefinitionIdAndActiveTrueOrderByDisplayOrderAsc(field.getId());
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).getOptionValue()).isEqualTo("corridor");

        List<FieldOption> allOptions =
                fieldOptionRepo.findByFieldDefinitionIdOrderByDisplayOrderAsc(field.getId());
        assertThat(allOptions).hasSize(2);
    }

    // =========================================================================
    // Tests — Active category filtering
    // =========================================================================

    /**
     * AC: Active filtering — inactive categories are excluded from active-only queries.
     */
    @Test
    void activeCategoryFilteringExcludesInactiveCategories() {
        IncidentCategory activeA = savedCategory("ACTIVE_CAT_A", "Active Category A");
        IncidentCategory activeB = savedCategory("ACTIVE_CAT_B", "Active Category B");

        IncidentCategory inactive = new IncidentCategory("INACTIVE_CAT", "Inactive Category");
        inactive.changeActive(false);
        entityManager.persistAndFlush(inactive);
        entityManager.clear();

        List<IncidentCategory> activeCategories = categoryRepo.findByActiveTrueOrderByName();

        assertThat(activeCategories)
                .extracting(IncidentCategory::getCode)
                .contains("ACTIVE_CAT_A", "ACTIVE_CAT_B")
                .doesNotContain("INACTIVE_CAT");
    }

    /**
     * Active categories are returned in alphabetical name order.
     */
    @Test
    void activeCategoriesAreOrderedByName() {
        savedCategory("ZZZ_CAT", "Zebra Category");
        savedCategory("AAA_CAT", "Apple Category");
        savedCategory("MMM_CAT", "Mango Category");
        entityManager.clear();

        List<IncidentCategory> result = categoryRepo.findByActiveTrueOrderByName();

        List<String> names = result.stream().map(IncidentCategory::getName).toList();
        assertThat(names).isSorted();
    }

    // =========================================================================
    // Tests — Published-form lookup
    // =========================================================================

    /**
     * AC: Published-form lookup — only the PUBLISHED version is returned; DRAFT is not.
     */
    @Test
    void publishedFormLookupReturnsSinglePublishedVersion() {
        IncidentCategory category = savedCategory("DOOR_LOCK_ISSUE");
        CategoryFormVersion draftV1 = savedFormVersion(category, 1, FormStatus.DRAFT);
        CategoryFormVersion publishedV2 = savedFormVersion(category, 2, FormStatus.PUBLISHED);
        entityManager.clear();

        Optional<CategoryFormVersion> published =
                formVersionRepo.findByCategoryIdAndFormStatus(category.getId(), FormStatus.PUBLISHED);
        assertThat(published).isPresent();
        assertThat(published.get().getId()).isEqualTo(publishedV2.getId());
        assertThat(published.get().getVersionNumber()).isEqualTo(2);

        Optional<CategoryFormVersion> draft =
                formVersionRepo.findByCategoryIdAndFormStatus(category.getId(), FormStatus.DRAFT);
        assertThat(draft).isPresent();
        assertThat(draft.get().getId()).isEqualTo(draftV1.getId());
    }

    /**
     * A category with no published form returns empty.
     */
    @Test
    void publishedFormLookupReturnsEmptyWhenNonePublished() {
        IncidentCategory category = savedCategory("WATER_LEAK");
        savedFormVersion(category, 1, FormStatus.DRAFT);
        entityManager.clear();

        Optional<CategoryFormVersion> published =
                formVersionRepo.findByCategoryIdAndFormStatus(category.getId(), FormStatus.PUBLISHED);
        assertThat(published).isEmpty();
    }

    /**
     * All form versions for a category are returned in ascending version_number order.
     */
    @Test
    void formVersionsAreReturnedInVersionNumberOrder() {
        IncidentCategory category = savedCategory("FIRE_EXTINGUISHER");
        savedFormVersion(category, 3, FormStatus.ARCHIVED);
        savedFormVersion(category, 1, FormStatus.ARCHIVED);
        savedFormVersion(category, 2, FormStatus.PUBLISHED);
        entityManager.clear();

        List<CategoryFormVersion> versions =
                formVersionRepo.findByCategoryIdOrderByVersionNumberAsc(category.getId());

        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(1);
        assertThat(versions.get(1).getVersionNumber()).isEqualTo(2);
        assertThat(versions.get(2).getVersionNumber()).isEqualTo(3);
    }

    // =========================================================================
    // Tests — validation_rules JSON preservation
    // =========================================================================

    /**
     * AC: validation_rules is preserved as valid JSON — the raw string is stored
     * and retrieved without modification.
     */
    @Test
    void validationRulesPreservedAsRawJsonString() {
        IncidentCategory category = savedCategory("CABLING_ISSUE");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);

        String jsonRules = "{\"min\":1,\"max\":100,\"pattern\":\"^[A-Z]{3}-\\\\d{4}$\"}";
        FieldDefinition field = new FieldDefinition(
                form, "asset_code", "Asset Code", DynamicFieldType.TEXT, 0);
        field.changeValidationRules(jsonRules);
        entityManager.persistAndFlush(field);
        entityManager.clear();

        List<FieldDefinition> loaded =
                fieldDefRepo.findByFormVersionIdOrderByDisplayOrderAsc(form.getId());

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getValidationRules()).isEqualTo(jsonRules);
    }

    /**
     * Default validation_rules is "{}" when not explicitly set.
     */
    @Test
    void defaultValidationRulesIsEmptyJsonObject() {
        IncidentCategory category = savedCategory("PEST_CONTROL");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);
        savedField(form, "description", 0);
        entityManager.clear();

        List<FieldDefinition> fields =
                fieldDefRepo.findByFormVersionIdOrderByDisplayOrderAsc(form.getId());

        assertThat(fields.get(0).getValidationRules()).isEqualTo("{}");
    }

    // =========================================================================
    // Tests — DRAFT form publishedAt is null
    // =========================================================================

    /**
     * AC: DRAFT form version has null publishedAt.
     */
    @Test
    void draftFormVersionHasNullPublishedAt() {
        IncidentCategory category = savedCategory("SOUND_SYSTEM_FAULT");
        CategoryFormVersion draft = savedFormVersion(category, 1, FormStatus.DRAFT);
        entityManager.clear();

        CategoryFormVersion loaded = formVersionRepo.findById(draft.getId()).orElseThrow();
        assertThat(loaded.getFormStatus()).isEqualTo(FormStatus.DRAFT);
        assertThat(loaded.getPublishedAt()).isNull();
        assertThat(loaded.getPublishedById()).isNull();
    }

    // =========================================================================
    // Tests — Uniqueness constraints
    // =========================================================================

    /**
     * AC: Category code uniqueness is enforced at the DB level.
     */
    @Test
    void uniqueCodePerCategoryIsEnforced() {
        savedCategory("DUPLICATE_CODE");

        IncidentCategory duplicate = new IncidentCategory("DUPLICATE_CODE", "Another Category");

        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicate))
                .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    /**
     * AC: Field key uniqueness per form version is enforced at the DB level.
     */
    @Test
    void uniqueFieldKeyPerFormVersionIsEnforced() {
        IncidentCategory category = savedCategory("HVAC_FAULT");
        CategoryFormVersion form = savedFormVersion(category, 1, FormStatus.DRAFT);
        savedField(form, "same_key", 0);

        FieldDefinition duplicate = new FieldDefinition(
                form, "same_key", "Duplicate Label", DynamicFieldType.TEXTAREA, 1);

        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicate))
                .isInstanceOf(jakarta.persistence.PersistenceException.class);
    }

    // =========================================================================
    // Tests — Cross-module narrow ID references
    // =========================================================================

    /**
     * AC: Cross-module ID references (createdById, defaultSkillId, defaultSlaPolicyId)
     * are stored and retrieved as-is without JPA association loading.
     */
    @Test
    void crossModuleIdReferencesArePreservedAsLong() {
        IncidentCategory category = savedCategory("WINDOW_FAULT");
        category.changeCreatedById(42L);
        entityManager.persistAndFlush(category);

        CategoryFormVersion form = new CategoryFormVersion(category, 1);
        form.changeDefaultSkillId(999L);
        form.changeDefaultSlaPolicyId(777L);
        form.changeCreatedById(42L);
        entityManager.persistAndFlush(form);
        entityManager.clear();

        CategoryFormVersion loaded = formVersionRepo.findById(form.getId()).orElseThrow();
        assertThat(loaded.getDefaultSkillId()).isEqualTo(999L);
        assertThat(loaded.getDefaultSlaPolicyId()).isEqualTo(777L);
        assertThat(loaded.getCreatedById()).isEqualTo(42L);

        IncidentCategory loadedCat = categoryRepo.findById(category.getId()).orElseThrow();
        assertThat(loadedCat.getCreatedById()).isEqualTo(42L);
    }

    // =========================================================================
    // Tests — SLA Policy repository
    // =========================================================================

    /**
     * Active SLA policies are returned ordered by name; inactive ones are excluded.
     */
    @Test
    void slaPoliciesActiveFilteringAndOrdering() {
        savedSlaPolicy("Standard — Normal", IssuePriority.NORMAL, 480);
        savedSlaPolicy("Premium — High", IssuePriority.HIGH, 120);

        SlaPolicy inactive = new SlaPolicy("Deprecated — Low", IssuePriority.LOW, 1440);
        inactive.changeActive(false);
        entityManager.persistAndFlush(inactive);
        entityManager.clear();

        List<SlaPolicy> active = slaPolicyRepo.findByActiveTrueOrderByPolicyName();
        assertThat(active).hasSize(2);
        assertThat(active.get(0).getPolicyName()).isEqualTo("Premium — High");
        assertThat(active.get(1).getPolicyName()).isEqualTo("Standard — Normal");

        assertThat(slaPolicyRepo.findByPolicyName("Deprecated — Low")).isPresent();
        assertThat(active)
                .extracting(SlaPolicy::getPolicyName)
                .doesNotContain("Deprecated — Low");
    }

    /**
     * SLA policy fields are fully persisted and retrievable.
     */
    @Test
    void slaPolicyFieldsArePersistedCorrectly() {
        SlaPolicy policy = savedSlaPolicy("Urgent SLA", IssuePriority.URGENT, 60);
        policy.changePauseWaitingRequester(true);
        entityManager.persistAndFlush(policy);
        entityManager.clear();

        SlaPolicy loaded = slaPolicyRepo.findByPolicyName("Urgent SLA").orElseThrow();
        assertThat(loaded.getPriority()).isEqualTo(IssuePriority.URGENT);
        assertThat(loaded.getResolutionMinutes()).isEqualTo(60);
        assertThat(loaded.isPauseWaitingRequester()).isTrue();
        assertThat(loaded.isActive()).isTrue();
    }

    /**
     * Category properties (assetPolicy, locationRequired, defaultPriority) are
     * persisted with their defaults and updateable.
     */
    @Test
    void categoryPropertiesAreMappedCorrectly() {
        IncidentCategory category = new IncidentCategory("PROJECTOR_LENS", "Projector Lens Fault");
        category.changeAssetPolicy(AssetPolicy.REQUIRED);
        category.changeLocationRequired(false);
        category.changeDefaultPriority(IssuePriority.HIGH);
        category.changeSlaHours(4);
        entityManager.persistAndFlush(category);
        entityManager.clear();

        IncidentCategory loaded = categoryRepo.findByCode("PROJECTOR_LENS").orElseThrow();
        assertThat(loaded.getAssetPolicy()).isEqualTo(AssetPolicy.REQUIRED);
        assertThat(loaded.isLocationRequired()).isFalse();
        assertThat(loaded.getDefaultPriority()).isEqualTo(IssuePriority.HIGH);
        assertThat(loaded.getSlaHours()).isEqualTo(4);
    }

    /**
     * CategoryFormVersion V6 fields (requiresEquipment, minAttachmentCount)
     * are persisted correctly.
     */
    @Test
    void formVersionV6FieldsAreMappedCorrectly() {
        IncidentCategory category = savedCategory("CCTV_FAULT");
        CategoryFormVersion form = new CategoryFormVersion(category, 1);
        form.changeRequiresEquipment(true);
        form.changeMinAttachmentCount(2);
        entityManager.persistAndFlush(form);
        entityManager.clear();

        CategoryFormVersion loaded = formVersionRepo.findById(form.getId()).orElseThrow();
        assertThat(loaded.isRequiresEquipment()).isTrue();
        assertThat(loaded.getMinAttachmentCount()).isEqualTo(2);
    }
}
