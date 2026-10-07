package com.nexora.category.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.*;
import com.nexora.category.repository.*;
import com.nexora.common.exception.*;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.sla.repository.SlaPolicyRepository;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.SkillRepository;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final IncidentCategoryRepository categories;
    private final CategoryFormVersionRepository forms;
    private final FieldDefinitionRepository fields;
    private final FieldOptionRepository options;
    private final SkillRepository skills;
    private final SlaPolicyRepository slas;
    private final ObjectMapper json;
    private final FormValidator validator;

    public CategoryService(IncidentCategoryRepository categories, CategoryFormVersionRepository forms,
            FieldDefinitionRepository fields, FieldOptionRepository options, SkillRepository skills,
            SlaPolicyRepository slas, ObjectMapper json, FormValidator validator) {
        this.categories = categories; this.forms = forms; this.fields = fields; this.options = options;
        this.skills = skills; this.slas = slas; this.json = json; this.validator = validator;
    }

    public List<CategoryResponse> categories(boolean admin) {
        return (admin ? categories.findAllByOrderByNameAscIdAsc() : categories.findByActiveTrueOrderByNameAscIdAsc())
                .stream().map(CategoryResponse::from).toList();
    }

    public List<SlaResponse> slaPolicies(boolean activeOnly) {
        return (activeOnly ? slas.findByActiveTrueOrderByPolicyNameAscIdAsc() : slas.findAll(Sort.by("policyName", "id"))).stream()
                .map(this::sla).toList();
    }

    public List<SkillResponse> skills() { return skills.findActiveOrderByNameAndCode().stream().map(this::skill).toList(); }

    public FormResponse published(Long categoryId) {
        IncidentCategory c = categories.findById(categoryId).filter(IncidentCategory::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active category not found: " + categoryId));
        CategoryFormVersion f = forms.findByCategoryIdAndFormStatus(c.getId(), FormStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessRuleException("This category has no published form."));
        return response(f, false);
    }

    public List<VersionResponse> versions(Long categoryId) {
        category(categoryId);
        return forms.findByCategoryIdOrderByVersionNumberAsc(categoryId).stream().map(f -> new VersionResponse(
                f.getId(), f.getVersionNumber(), f.getFormStatus(), f.getLockVersion(), f.getCreatedAt(), f.getPublishedAt())).toList();
    }

    public FormResponse form(Long id) { return response(findForm(id), true); }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest r, Long actorId) {
        if (categories.findByCode(r.code()).isPresent()) throw new FormValidationException(Map.of("code", "Category code already exists."));
        IncidentCategory c = new IncidentCategory(r.code(), r.name());
        applyCategory(c, r); c.changeCreatedById(actorId);
        return CategoryResponse.from(categories.saveAndFlush(c));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest r) {
        IncidentCategory c = lockCategory(id);
        if (!c.getCode().equals(r.code())) throw new FormValidationException(Map.of("code", "Category code is immutable."));
        if (r.assetPolicy() == AssetPolicy.FORBIDDEN && forms.findByCategoryIdAndFormStatus(id, FormStatus.PUBLISHED)
                .map(CategoryFormVersion::isRequiresEquipment).orElse(false))
            throw new FormValidationException(Map.of("assetPolicy", "The published form requires equipment. Publish a compatible form first."));
        applyCategory(c, r);
        return CategoryResponse.from(categories.saveAndFlush(c));
    }

    private void applyCategory(IncidentCategory c, CategoryRequest r) {
        c.changeName(r.name()); c.changeDescription(r.description()); c.changeAssetPolicy(r.assetPolicy());
        c.changeLocationRequired(r.locationRequired()); c.changeDefaultPriority(r.defaultPriority()); c.changeActive(r.active());
    }

    @Transactional
    public FormResponse createDraft(Long categoryId, Long actorId) {
        IncidentCategory c = lockCategory(categoryId);
        CategoryFormVersion f = new CategoryFormVersion(c, forms.maximumVersion(categoryId) + 1);
        f.changeCreatedById(actorId);
        return response(forms.saveAndFlush(f), true);
    }

    @Transactional
    public FormResponse updateDraft(Long id, DraftRequest r) {
        CategoryFormVersion f = lockForm(id);
        checkRevision(f, r.revision()); requireStatus(f, FormStatus.DRAFT);
        Map<String, String> errors = validator.errors(r, false);
        if (r.defaultSkillId() != null && !skills.existsById(r.defaultSkillId())) errors.put("defaultSkillId", "Skill not found.");
        if (r.defaultSlaPolicyId() != null && !slas.existsById(r.defaultSlaPolicyId())) errors.put("defaultSlaPolicyId", "SLA policy not found.");
        reject(errors);
        // Only drafts can reach this replacement; published field/option IDs stay untouched forever.
        options.deleteForForm(id); fields.deleteForForm(id); fields.flush();
        f.changeRequiresEquipment(r.requiresEquipment()); f.changeMinAttachmentCount(r.minAttachments());
        f.changeDefaultSkillId(r.defaultSkillId()); f.changeDefaultSlaPolicyId(r.defaultSlaPolicyId()); f.touch();
        for (FieldRequest field : r.fields()) {
            FieldDefinition d = new FieldDefinition(f, field.fieldKey(), field.label(), field.fieldType(), field.displayOrder());
            d.changeHelpText(field.helpText()); d.changePlaceholder(field.placeholder()); d.changeRequired(field.required());
            d.changeActive(field.active()); d.changeValidationRules(field.validationRules().toString());
            fields.save(d);
            for (OptionRequest option : field.options()) {
                FieldOption o = new FieldOption(d, option.optionValue(), option.optionLabel(), option.displayOrder());
                o.changeActive(option.active()); options.save(o);
            }
        }
        forms.flush();
        return response(f, true);
    }

    @Transactional
    public FormResponse publish(Long id, long revision, Long actorId) {
        CategoryFormVersion f = lockForm(id);
        checkRevision(f, revision); requireStatus(f, FormStatus.DRAFT);
        FormResponse current = response(f, true);
        DraftRequest r = new DraftRequest(revision, current.requiresEquipment(), current.minAttachments(), current.defaultSkillId(),
                current.defaultSlaPolicyId(), current.fields().stream().map(d -> new FieldRequest(d.fieldKey(), d.label(), d.fieldType(),
                        d.helpText(), d.placeholder(), d.required(), d.active(), d.displayOrder(), d.validationRules(),
                        d.options().stream().map(o -> new OptionRequest(o.optionValue(), o.optionLabel(), o.displayOrder(), o.active())).toList())).toList());
        Map<String, String> errors = validator.errors(r, true);
        if (!f.getCategory().isActive()) errors.put("category.active", "Activate the category before publishing.");
        if (r.requiresEquipment() && f.getCategory().getAssetPolicy() == AssetPolicy.FORBIDDEN)
            errors.put("requiresEquipment", "Equipment cannot be required when assets are forbidden.");
        if (r.defaultSkillId() == null || skills.findById(r.defaultSkillId()).filter(Skill::isActive).isEmpty())
            errors.put("defaultSkillId", "Choose an active default skill.");
        if (r.defaultSlaPolicyId() == null || slas.findById(r.defaultSlaPolicyId()).filter(SlaPolicy::isActive).isEmpty())
            errors.put("defaultSlaPolicyId", "Choose an active SLA policy.");
        reject(errors);
        forms.findByCategoryIdAndFormStatus(f.getCategory().getId(), FormStatus.PUBLISHED).ifPresent(old -> {
            old.changeFormStatus(FormStatus.ARCHIVED); old.touch();
        });
        // Flush the archive before publishing to satisfy SQL Server's filtered unique index.
        forms.flush();
        f.changePublishingMetadata(actorId, OffsetDateTime.now(ZoneOffset.UTC));
        f.changeFormStatus(FormStatus.PUBLISHED); f.touch(); forms.flush();
        return response(f, true);
    }

    @Transactional
    public FormResponse archive(Long id, long revision) {
        CategoryFormVersion f = lockForm(id);
        checkRevision(f, revision); requireStatus(f, FormStatus.PUBLISHED);
        f.changeFormStatus(FormStatus.ARCHIVED); f.touch(); forms.flush();
        return response(f, true);
    }

    private CategoryFormVersion lockForm(Long id) {
        Long categoryId = forms.findCategoryId(id).orElseThrow(() -> new ResourceNotFoundException("Form not found: " + id));
        lockCategory(categoryId);
        return findForm(id);
    }

    private IncidentCategory lockCategory(Long id) { return categories.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)); }
    private IncidentCategory category(Long id) { return categories.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id)); }
    private CategoryFormVersion findForm(Long id) { return forms.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Form not found: " + id)); }
    private void checkRevision(CategoryFormVersion f, Long revision) {
        if (revision == null || f.getLockVersion() != revision) throw new StaleWriteException();
    }
    private void requireStatus(CategoryFormVersion f, FormStatus status) {
        if (f.getFormStatus() != status) throw new BusinessRuleException("This operation requires a " + status + " form. Published and archived form content is immutable.");
    }
    private void reject(Map<String, String> errors) { if (!errors.isEmpty()) throw new FormValidationException(errors); }

    private FormResponse response(CategoryFormVersion f, boolean admin) {
        Map<Long, List<FieldOption>> byField = options.findByFieldDefinitionFormVersionIdOrderByDisplayOrderAscIdAsc(f.getId())
                .stream().collect(Collectors.groupingBy(o -> o.getFieldDefinition().getId()));
        List<FieldResponse> definitions = fields.findByFormVersionIdOrderByDisplayOrderAscIdAsc(f.getId()).stream()
                .filter(d -> admin || d.isActive()).map(d -> new FieldResponse(d.getId(), d.getFieldKey(), d.getLabel(), d.getFieldType(),
                        d.getHelpText(), d.getPlaceholder(), d.isRequired(), d.isActive(), d.getDisplayOrder(), parseRules(d),
                        byField.getOrDefault(d.getId(), List.of()).stream().filter(o -> admin || o.isActive())
                                .map(o -> new OptionResponse(o.getId(), o.getOptionValue(), o.getOptionLabel(), o.getDisplayOrder(), o.isActive())).toList())).toList();
        SkillResponse defaultSkill = f.getDefaultSkillId() == null ? null : skills.findById(f.getDefaultSkillId()).map(this::skill).orElse(null);
        SlaResponse defaultSla = f.getDefaultSlaPolicyId() == null ? null : slas.findById(f.getDefaultSlaPolicyId()).map(this::sla).orElse(null);
        return new FormResponse(1, f.getId(), CategoryResponse.from(f.getCategory()), f.getVersionNumber(), f.getFormStatus(),
                f.getLockVersion(), f.isRequiresEquipment(), f.getMinAttachmentCount(), f.getDefaultSkillId(), f.getDefaultSlaPolicyId(),
                defaultSkill, defaultSla, f.getCreatedAt(), f.getPublishedAt(), definitions);
    }

    private JsonNode parseRules(FieldDefinition field) {
        try {
            JsonNode rules = json.readTree(field.getValidationRules());
            Map<String, String> errors = new LinkedHashMap<>();
            validator.rules(field.getFieldType(), rules, "fields." + field.getFieldKey() + ".validationRules", errors);
            reject(errors);
            return rules;
        } catch (JsonProcessingException ex) {
            throw new FormValidationException(Map.of("fields." + field.getFieldKey() + ".validationRules", "Stored validation rules are not valid JSON."));
        }
    }
    private SkillResponse skill(Skill s) { return new SkillResponse(s.getId(), s.getCode(), s.getName(), s.isActive()); }
    private SlaResponse sla(SlaPolicy s) { return new SlaResponse(s.getId(), s.getPolicyName(), s.getPriority(), s.getResolutionMinutes(), s.isPauseWaitingRequester(), s.isActive()); }
}
