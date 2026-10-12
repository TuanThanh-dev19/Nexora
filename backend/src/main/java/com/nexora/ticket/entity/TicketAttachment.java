package com.nexora.ticket.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import com.nexora.category.entity.FieldDefinition;
import com.nexora.user.entity.AppUser;

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
 * File metadata only. The storage key is opaque and the original name is display
 * data, never a filesystem path. No file access or binary content belongs here.
 *
 * <p>SQL Server protects the composite ticket/form/field references. A field-linked
 * IMAGE uses REPORT_EVIDENCE; COMMENT is retained for schema compatibility only.
 */
@Entity
@Table(name = "ticket_attachments", schema = "dbo", uniqueConstraints =
        @UniqueConstraint(name = "uq_ticket_attachments_storage_key", columnNames = "storage_key"))
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @Column(name = "form_version_id", nullable = false, updatable = false)
    private Long formVersionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_definition_id", updatable = false)
    private FieldDefinition fieldDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
    private AppUser uploadedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "attachment_type", nullable = false, length = 30, updatable = false)
    private AttachmentType attachmentType;

    @Nationalized
    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Nationalized
    @Column(name = "original_name", nullable = false, length = 255, updatable = false)
    private String originalName;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 120, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TicketAttachment() {
    }

    public TicketAttachment(Ticket ticket, FieldDefinition fieldDefinition, AppUser uploadedBy,
            AttachmentType attachmentType, String storageKey, String originalName,
            String contentType, long sizeBytes) {
        this(ticket, fieldDefinition, uploadedBy, attachmentType, storageKey, originalName,
                contentType, sizeBytes, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public TicketAttachment(Ticket ticket, FieldDefinition fieldDefinition, AppUser uploadedBy,
            AttachmentType attachmentType, String storageKey, String originalName,
            String contentType, long sizeBytes, OffsetDateTime createdAt) {
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(ticket.getId(), "ticket must be persisted before adding an attachment");
        this.formVersionId = Objects.requireNonNull(
                ticket.getFormVersion().getId(), "ticket formVersion must be persisted");
        if (fieldDefinition != null) {
            Objects.requireNonNull(fieldDefinition.getId(), "fieldDefinition must be persisted");
            if (!formVersionId.equals(fieldDefinition.getFormVersion().getId())) {
                throw new IllegalArgumentException("fieldDefinition must belong to ticket formVersion");
            }
        }
        this.fieldDefinition = fieldDefinition;
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy is required");
        Objects.requireNonNull(uploadedBy.getId(), "uploadedBy must be persisted before adding an attachment");
        this.attachmentType = Objects.requireNonNull(attachmentType, "attachmentType is required");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey is required");
        if (storageKey.isBlank() || storageKey.length() > 500) {
            throw new IllegalArgumentException("storageKey must contain between 1 and 500 characters");
        }
        this.originalName = Objects.requireNonNull(originalName, "originalName is required");
        this.contentType = Objects.requireNonNull(contentType, "contentType is required");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be > 0");
        }
        this.sizeBytes = sizeBytes;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
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

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "TicketAttachment{id=" + id + ", attachmentType=" + attachmentType
                + ", sizeBytes=" + sizeBytes + "}";
    }
}
