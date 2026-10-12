package com.nexora.category.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A selectable option for a {@link FieldDefinition} of type
 * {@link DynamicFieldType#SELECT} or {@link DynamicFieldType#MULTI_SELECT}.
 *
 * <p>Maps to {@code dbo.field_options} (created in V3 migration).
 *
 * <h3>Ordering</h3>
 * <p>Options are retrieved in deterministic order using {@link #displayOrder}.
 * Use {@code FieldOptionRepository#findByFieldDefinitionIdAndActiveTrueOrderByDisplayOrderAsc}
 * to obtain active options in the correct presentation sequence.
 *
 * <h3>No bidirectional navigation</h3>
 * <p>{@link FieldDefinition} does not hold a collection of options.
 * Use {@code FieldOptionRepository} to query by field definition.
 */
@Entity
@Table(
        name = "field_options",
        schema = "dbo",
        uniqueConstraints = {
                // uq_field_options_value: option_value unique per field definition
                @UniqueConstraint(name = "uq_field_options_value",
                        columnNames = {"field_definition_id", "option_value"})
        }
)
public class FieldOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The field definition that owns this option.
     * Loaded lazily to avoid fetching all options when only the field is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_definition_id", nullable = false)
    private FieldDefinition fieldDefinition;

    /**
     * The machine-readable value stored when the reporter selects this option
     * (e.g. {@code "high_priority"}). Unique per field definition,
     * enforced by {@code uq_field_options_value}.
     */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "option_value", nullable = false, length = 100)
    private String optionValue;

    /** The human-readable text displayed to the reporter in the dropdown. */
    @Nationalized
    @Column(name = "option_label", nullable = false, length = 150)
    private String optionLabel;

    /**
     * Zero-based position of this option within the field's option list.
     * Lower values appear first. Must be {@code >= 0}
     * (validated by {@code ck_field_options_display_order}).
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Whether this option is currently available for selection.
     * Inactive options are hidden from reporters but retained for historical data.
     * Default: {@code true}.
     */
    @Column(nullable = false)
    private boolean active = true;

    /** Required by JPA — not for direct use. */
    protected FieldOption() {
    }

    /**
     * Creates a new active field option.
     *
     * @param fieldDefinition the owning field; must not be {@code null}
     * @param optionValue     machine-readable stored value; must not be {@code null}
     * @param optionLabel     display label; must not be {@code null}
     * @param displayOrder    zero-based position; must be {@code >= 0}
     */
    public FieldOption(FieldDefinition fieldDefinition, String optionValue,
                       String optionLabel, int displayOrder) {
        this.fieldDefinition = Objects.requireNonNull(fieldDefinition, "fieldDefinition is required");
        this.optionValue = Objects.requireNonNull(optionValue, "optionValue is required");
        this.optionLabel = Objects.requireNonNull(optionLabel, "optionLabel is required");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be >= 0");
        }
        this.displayOrder = displayOrder;
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /** Updates the display label. Must not be {@code null}. */
    public void changeOptionLabel(String optionLabel) {
        this.optionLabel = Objects.requireNonNull(optionLabel, "optionLabel is required");
    }

    /** Updates the display position. Must be {@code >= 0}. */
    public void changeDisplayOrder(int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("displayOrder must be >= 0");
        }
        this.displayOrder = displayOrder;
    }

    /** Activates or deactivates this option. */
    public void changeActive(boolean active) {
        this.active = active;
    }

    // -------------------------------------------------------------------------
    // Accessors — read-only
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public FieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "FieldOption{id=" + id + ", optionValue='" + optionValue
                + "', displayOrder=" + displayOrder + ", active=" + active + "}";
    }
}
