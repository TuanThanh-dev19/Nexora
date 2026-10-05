package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import com.nexora.category.entity.FieldDefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A dynamic answer stored as verbatim JSON, without parsing or re-serialization.
 *
 * <p>SQL Server validates JSON syntax and the composite ticket/form/field FKs.
 * Field-type, required, option and validation-rule checks belong to the later
 * ticket-creation service. IMAGE fields are represented by field-linked
 * attachments and do not require a value row.
 */
@Entity
@Table(name = "ticket_field_values", schema = "dbo", uniqueConstraints =
        @UniqueConstraint(name = "uq_ticket_field_values_field",
                columnNames = {"ticket_id", "field_definition_id"}))
public class TicketFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Column(name = "form_version_id", nullable = false, updatable = false)
    private Long formVersionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_definition_id", nullable = false, updatable = false)
    private FieldDefinition fieldDefinition;

    @Column(name = "value_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String valueJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TicketFieldValue() {
    }

    /** References must already be persisted; no reference is cascade-persisted. */
    public TicketFieldValue(Ticket ticket, FieldDefinition fieldDefinition, String valueJson) {
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(ticket.getId(), "ticket must be persisted before adding a value");
        this.fieldDefinition = Objects.requireNonNull(fieldDefinition, "fieldDefinition is required");
        Objects.requireNonNull(fieldDefinition.getId(), "fieldDefinition must be persisted before adding a value");
        this.formVersionId = Objects.requireNonNull(
                ticket.getFormVersion().getId(), "ticket formVersion must be persisted");
        if (!formVersionId.equals(fieldDefinition.getFormVersion().getId())) {
            throw new IllegalArgumentException("fieldDefinition must belong to ticket formVersion");
        }
        this.valueJson = Objects.requireNonNull(valueJson, "valueJson is required");
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public Long getFormVersionId() {
        return formVersionId;
    }

    public FieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public String getValueJson() {
        return valueJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TicketFieldValue{id=" + id + ", formVersionId=" + formVersionId + "}";
    }
}
