package com.nexora.category.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A single input field within a {@link CategoryFormVersion}.
 *
 * <p>Maps to {@code dbo.field_definitions} (created in V3 migration).
 *
 * <h3>Ordering</h3>
 * <p>Fields are retrieved in deterministic order using {@link #displayOrder}.
 * Use {@code FieldDefinitionRepository#findByFormVersionIdOrderByDisplayOrderAsc}
 * to obtain fields in the correct display sequence.
 *
 * <h3>{@code validation_rules} contract</h3>
 * <p>{@link #validationRules} is stored and returned as a <em>raw JSON string</em>.
 * It is never automatically deserialized or re-serialized by the persistence layer,
 * ensuring that its content is never silently modified. The DB check constraint
 * {@code ck_field_definitions_validation_json} guarantees that only valid JSON
 * objects are accepted. Callers that need the rules parsed must do so explicitly.
 * This field is intentionally not exposed as a structured API contract in this issue.
 *
 * <h3>No bidirectional navigation</h3>
 * <p>{@link CategoryFormVersion} does not hold a collection of field definitions.
 * Use {@code FieldDefinitionRepository} to query by form version.
 */
@Entity
@Table(
        name = "field_definitions",
        schema = "dbo",
        uniqueConstraints = {
                // uq_field_definitions_key: unique field_key within a form version
                @UniqueConstraint(name = "uq_field_definitions_key",
                        columnNames = {"form_version_id", "field_key"}),
                // uq_field_definitions_form_id: used by ticket_attachments FK
                @UniqueConstraint(name = "uq_field_definitions_form_id",
                        columnNames = {"form_version_id", "id"})
        }
)
public class FieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The form version that owns this field.
     * Loaded lazily to avoid fetching all fields when only the form is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_version_id", nullable = false)
    private CategoryFormVersion formVersion;

    /**
     * Machine-readable key identifying this field within the form
     * (e.g. "asset_serial_number"). Unique per form version,
     * enforced by {@code uq_field_definitions_key}.
     */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    /** Human-readable label displayed to the reporter. */
    @Nationalized
    @Column(nullable = false, length = 150)
    private String label;

    /**
     * The UI input type for this field.
     * Validated by {@code ck_field_definitions_type}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "field_type", nullable = false, length = 30)
    private DynamicFieldType fieldType;

    /** Optional explanatory text displayed below the input. */
    @Nationalized
    @Column(name = "help_text", length = 500)
    private String helpText;

    /** Optional placeholder text inside the input control. */
    @Nationalized
    @Column(length = 250)
    private String placeholder;

    /** Whether the reporter must fill in this field before submitting. Default: {@code false}. */
    @Column(nullable = false)
    private boolean required = false;

    /**
     * Zero-based position of this field within the form.
     * Lower values appear first. Must be {@code >= 0}
     * (validated by {@code ck_field_definitions_display_order}).
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * JSON object containing field-specific validation constraints
     * (e.g. {@code {"min":1,"max":100,"pattern":"^[A-Z]+"} }).
     *
     * <p><strong>Persistence contract:</strong> this value is stored and
     * retrieved as a verbatim {@code NVARCHAR(MAX)} string. The persistence
     * layer never parses, re-serializes, or modifies its content.
     * Default value is {@code "{}"} (empty object), set by the DB default
     * constraint {@code df_field_definitions_validation}.
     *
     * <p>Do not expose this field as a structured API contract without an
     * explicit versioned schema.
     */
    @Column(name = "validation_rules", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String validationRules = "{}";

    /** Required by JPA — not for direct use. */
    protected FieldDefinition() {
    }

    /**
     * Creates a new field definition.
     *
     * @param formVersion  owning form version; must not be {@code null}
     * @param fieldKey     unique key within the form; must not be {@code null}
     * @param label        display label; must not be {@code null}
     * @param fieldType    input type; must not be {@code null}
     * @param displayOrder zero-based position; must be {@code >= 0}
     */
    public FieldDefinition(CategoryFormVersion formVersion, String fieldKey,
                           String label, DynamicFieldType fieldType, int displayOrder) {
        this.formVersion = Objects.requireNonNull(formVersion, "formVersion is required");
        this.fieldKey = Objects.requireNonNull(fieldKey, "fieldKey is required");
        this.label = Objects.requireNonNull(label, "label is required");
        this.fieldType = Objects.requireNonNull(fieldType, "fieldType is required");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be >= 0");
        }
        this.displayOrder = displayOrder;
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /** Updates the display label. Must not be {@code null}. */
    public void changeLabel(String label) {
        this.label = Objects.requireNonNull(label, "label is required");
    }

    /** Sets or clears the optional help text. */
    public void changeHelpText(String helpText) {
        this.helpText = helpText;
    }

    /** Sets or clears the optional placeholder text. */
    public void changePlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    /** Sets whether this field is required. */
    public void changeRequired(boolean required) {
        this.required = required;
    }

    /** Updates the display position. Must be {@code >= 0}. */
    public void changeDisplayOrder(int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be >= 0");
        }
        this.displayOrder = displayOrder;
    }

    /**
     * Replaces the raw JSON validation rules string.
     * The caller is responsible for providing a valid JSON object string.
     * Must not be {@code null}; use {@code "{}"} for no rules.
     */
    public void changeValidationRules(String validationRules) {
        this.validationRules = Objects.requireNonNull(validationRules, "validationRules must not be null");
    }

    // -------------------------------------------------------------------------
    // Accessors — read-only
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public CategoryFormVersion getFormVersion() {
        return formVersion;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public DynamicFieldType getFieldType() {
        return fieldType;
    }

    public String getHelpText() {
        return helpText;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public boolean isRequired() {
        return required;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    /**
     * Returns the raw JSON validation rules string exactly as stored.
     * Never {@code null}; defaults to {@code "{}"}.
     */
    public String getValidationRules() {
        return validationRules;
    }

    @Override
    public String toString() {
        return "FieldDefinition{id=" + id + ", fieldKey='" + fieldKey
                + "', fieldType=" + fieldType + ", displayOrder=" + displayOrder + "}";
    }
}
