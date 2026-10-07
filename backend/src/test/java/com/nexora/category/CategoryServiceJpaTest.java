package com.nexora.category;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.*;
import com.nexora.category.repository.*;
import com.nexora.category.service.*;
import com.nexora.common.exception.*;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.sla.repository.SlaPolicyRepository;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.SkillRepository;
import com.nexora.testsupport.NexoraJpaTest;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@NexoraJpaTest
@Import({CategoryService.class, FormValidator.class, CategoryServiceJpaTest.Config.class})
class CategoryServiceJpaTest {
    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
    }
    @Autowired CategoryService service;
    @Autowired IncidentCategoryRepository categories;
    @Autowired CategoryFormVersionRepository forms;
    @Autowired FieldDefinitionRepository fields;
    @Autowired FieldOptionRepository options;
    @Autowired SkillRepository skills;
    @Autowired SlaPolicyRepository slas;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    Long categoryId, skillId, slaId;

    @BeforeEach void setup() {
        categoryId = categories.saveAndFlush(new IncidentCategory("TEST_CATEGORY", "Test category")).getId();
        skillId = skills.saveAndFlush(new Skill("TEST_SKILL", "Test skill")).getId();
        slaId = slas.saveAndFlush(new SlaPolicy("Test SLA", IssuePriority.NORMAL, 60)).getId();
    }

    FieldRequest field(String key, DynamicFieldType type, int order, boolean active, String rules, List<OptionRequest> opts) throws Exception {
        return new FieldRequest(key, key, type, "Help", "Placeholder", true, active, order, json.readTree(rules), opts);
    }
    DraftRequest request(FormResponse form, List<FieldRequest> fs) { return new DraftRequest(form.revision(), false, 0, skillId, slaId, fs); }
    FormResponse savedDraft() throws Exception {
        FormResponse draft = service.createDraft(categoryId, null);
        return service.updateDraft(draft.id(), request(draft, List.of(field("description", DynamicFieldType.TEXTAREA, 0, true, "{\"minLength\":3,\"maxLength\":100}", List.of()))));
    }

    @Test void lifecyclePreservesOldPublishedFieldsAndArchivesOldForm() throws Exception {
        FormResponse first = savedDraft();
        first = service.publish(first.id(), first.revision(), null);
        Long oldFieldId = first.fields().getFirst().id();
        FormResponse second = savedDraft();
        assertThat(second.versionNumber()).isEqualTo(2);
        service.publish(second.id(), second.revision(), null);
        em.clear();
        assertThat(service.form(first.id()).status()).isEqualTo(FormStatus.ARCHIVED);
        assertThat(service.form(first.id()).fields().getFirst().id()).isEqualTo(oldFieldId);
        assertThat(service.published(categoryId).id()).isEqualTo(second.id());
        FormResponse published = service.form(second.id());
        service.archive(published.id(), published.revision());
        assertThatThrownBy(() -> service.published(categoryId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("no published form");
    }

    @Test void publicReadsExcludeInactiveFieldsOptionsAndUseStableOrderIncludingTies() throws Exception {
        FormResponse draft = service.createDraft(categoryId, null);
        FormResponse saved = service.updateDraft(draft.id(), request(draft, List.of(
                field("last", DynamicFieldType.TEXT, 9, true, "{}", List.of()),
                field("hidden", DynamicFieldType.TEXT, 0, false, "{}", List.of()),
                field("first", DynamicFieldType.SELECT, 1, true, "{}", List.of(
                        new OptionRequest("z", "Last", 8, true), new OptionRequest("hidden", "Hidden", 0, false),
                        new OptionRequest("a", "First", 1, true), new OptionRequest("b", "Second", 1, true))),
                field("tie", DynamicFieldType.TEXT, 1, true, "{}", List.of()))));
        service.publish(saved.id(), saved.revision(), null);
        FormResponse result = service.published(categoryId);
        assertThat(result.fields()).extracting(FieldResponse::fieldKey).containsExactly("first", "tie", "last");
        assertThat(result.fields().getFirst().options()).extracting(OptionResponse::optionValue).containsExactly("a", "b", "z");
        assertThat(service.form(saved.id()).fields()).hasSize(4);
        assertThat(service.form(saved.id()).fields().get(1).options()).hasSize(4);
    }

    @Test void onlyCurrentPublishedFormIsVisibleAndCategoryMustBeActive() throws Exception {
        FormResponse first = savedDraft();
        assertThatThrownBy(() -> service.published(categoryId)).isInstanceOf(BusinessRuleException.class);
        service.publish(first.id(), first.revision(), null);
        service.createDraft(categoryId, null);
        assertThat(service.published(categoryId).id()).isEqualTo(first.id());
        categories.findById(categoryId).orElseThrow().changeActive(false);
        categories.flush();
        assertThat(service.categories(false)).isEmpty();
        assertThat(service.categories(true)).hasSize(1);
        assertThatThrownBy(() -> service.published(categoryId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.published(-1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.versions(-1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void fieldOnlyEditsIncrementRevisionAndStaleSavesOrPublishReturnConflict() throws Exception {
        FormResponse draft = savedDraft();
        FormResponse changed = service.updateDraft(draft.id(), request(draft, List.of(field("newKey", DynamicFieldType.TEXT, 0, true, "{}", List.of()))));
        assertThat(changed.revision()).isGreaterThan(draft.revision());
        assertThatThrownBy(() -> service.updateDraft(draft.id(), request(draft, List.of()))).isInstanceOf(StaleWriteException.class);
        assertThatThrownBy(() -> service.publish(draft.id(), draft.revision(), null)).isInstanceOf(StaleWriteException.class);
    }

    @Test void publishedAndArchivedContentCannotBeEdited() throws Exception {
        FormResponse draft = savedDraft();
        FormResponse published = service.publish(draft.id(), draft.revision(), null);
        assertThatThrownBy(() -> service.updateDraft(published.id(), request(published, List.of()))).isInstanceOf(BusinessRuleException.class);
        FormResponse archived = service.archive(published.id(), published.revision());
        assertThatThrownBy(() -> service.updateDraft(archived.id(), request(archived, List.of()))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.publish(archived.id(), archived.revision(), null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void invalidNewPublishLeavesPreviousPublishedFormUntouched() throws Exception {
        FormResponse first = savedDraft(); service.publish(first.id(), first.revision(), null);
        FormResponse second = service.createDraft(categoryId, null);
        second = service.updateDraft(second.id(), request(second, List.of(field("select", DynamicFieldType.SELECT, 0, true, "{}", List.of()))));
        FormResponse invalid = second;
        assertThatThrownBy(() -> service.publish(invalid.id(), invalid.revision(), null)).isInstanceOfSatisfying(FormValidationException.class,
                e -> assertThat(e.getFieldErrors()).containsKey("fields[0].options"));
        assertThat(service.published(categoryId).id()).isEqualTo(first.id());
        assertThat(service.form(second.id()).status()).isEqualTo(FormStatus.DRAFT);
    }

    @Test void publishCollectsCategoryEquipmentSkillAndSlaErrors() throws Exception {
        FormResponse draft = savedDraft();
        draft = service.updateDraft(draft.id(), new DraftRequest(draft.revision(), true, 0, skillId, slaId, List.of()));
        IncidentCategory category = categories.findById(categoryId).orElseThrow();
        category.changeActive(false); category.changeAssetPolicy(AssetPolicy.FORBIDDEN);
        skills.findById(skillId).orElseThrow().changeActive(false);
        slas.findById(slaId).orElseThrow().changeActive(false);
        FormResponse invalid = draft;
        assertThatThrownBy(() -> service.publish(invalid.id(), invalid.revision(), null)).isInstanceOfSatisfying(FormValidationException.class,
                e -> assertThat(e.getFieldErrors()).containsKeys("category.active", "requiresEquipment", "defaultSkillId", "defaultSlaPolicyId"));
    }

    @Test void malformedRulesAreNeverSilentlyReplacedAndSavedRulesKeepTheirValues() throws Exception {
        FormResponse draft = savedDraft();
        DraftRequest invalid = request(draft, List.of(field("bad", DynamicFieldType.TEXT, 0, true, "{\"unknown\":42}", List.of())));
        assertThatThrownBy(() -> service.updateDraft(draft.id(), invalid)).isInstanceOf(FormValidationException.class);
        assertThat(service.form(draft.id()).fields().getFirst().fieldKey()).isEqualTo("description");
        assertThat(service.form(draft.id()).fields().getFirst().validationRules()).isEqualTo(json.readTree("{\"minLength\":3,\"maxLength\":100}"));
        FieldDefinition stored = fields.findByFormVersionIdOrderByDisplayOrderAscIdAsc(draft.id()).getFirst();
        stored.changeValidationRules("[]"); fields.flush();
        assertThatThrownBy(() -> service.form(draft.id())).isInstanceOf(FormValidationException.class);
    }

    @Test void categoryCreateUpdateAndReferenceLists() {
        CategoryResponse created = service.createCategory(new CategoryRequest("NEW", "New category", "Description", AssetPolicy.REQUIRED, true, IssuePriority.HIGH, true), null);
        CategoryResponse updated = service.updateCategory(created.id(), new CategoryRequest("NEW", "Renamed", "Changed", AssetPolicy.OPTIONAL, false, IssuePriority.LOW, false));
        assertThat(updated.name()).isEqualTo("Renamed"); assertThat(updated.active()).isFalse();
        assertThat(service.categories(false)).extracting(CategoryResponse::code).doesNotContain("NEW");
        assertThat(service.skills()).hasSize(1); assertThat(service.slaPolicies(true)).hasSize(1);
        slas.findById(slaId).orElseThrow().changeActive(false); slas.flush();
        assertThat(service.slaPolicies(true)).isEmpty(); assertThat(service.slaPolicies(false)).hasSize(1);
        assertThatThrownBy(() -> service.updateCategory(created.id(), new CategoryRequest("OTHER", "Name", null, AssetPolicy.OPTIONAL, true, IssuePriority.NORMAL, true))).isInstanceOf(FormValidationException.class);
    }
}
