package com.nexora.category.entity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A versioned dynamic form associated with an {@link IncidentCategory}.
 *
 * <p>Maps to {@code dbo.category_form_versions} (created in V3, extended in V6).
 *
 * <h3>Lifecycle</h3>
 * <p>A form starts as {@link FormStatus#DRAFT}, may be published (at most one
 * PUBLISHED form per category — enforced by {@code uq_category_form_versions_one_published}
 * filtered unique index at the DB level), and eventually archived.
 * PUBLISHED and ARCHIVED forms are immutable at the business layer; no service
 * in this issue implements those transitions.
 *
 * <h3>Cross-module narrow references</h3>
 * <ul>
 *   <li>{@link #createdById} / {@link #publishedById} — {@code app_users.id} stored as
 *       {@code Long}; no JPA {@code @ManyToOne} to avoid cross-module coupling.</li>
 *   <li>{@link #defaultSkillId} — {@code skills.id} stored as {@code Long}.
 *       Contract to be finalised in M1-06.</li>
 *   <li>{@link #defaultSlaPolicyId} — {@code sla_policies.id} stored as {@code Long}.
 *       Authoritative SLA reference for tickets created from this form version.</li>
 * </ul>
 *
 * <h3>No bidirectional navigation</h3>
 * <p>{@link IncidentCategory} does not hold a collection of form versions.
 * Use {@code CategoryFormVersionRepository} to query by category.
 */
@Entity
@Table(
        name = "category_form_versions",
        schema = "dbo",
        uniqueConstraints = {
                // uq_category_form_versions_number: version_number unique per category
                @UniqueConstraint(name = "uq_category_form_versions_number",
                        columnNames = {"category_id", "version_number"}),
                // uq_category_form_versions_category_id: used by ticket FK chains
                @UniqueConstraint(name = "uq_category_form_versions_category_id",
                        columnNames = {"category_id", "id"})
        }
)
public class CategoryFormVersion {

    @jakarta.persistence.Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    public long getLockVersion() { return lockVersion; }

    /** Makes field-only draft replacements participate in optimistic locking. */
    public void touch() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The category this form version belongs to.
     * Loaded lazily to keep the initial fetch lightweight.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private IncidentCategory category;

    /**
     * Monotonically increasing version number within the category scope.
     * Unique per category, enforced by {@code uq_category_form_versions_number}.
     */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /**
     * Current lifecycle state of this form version.
     * Default: {@link FormStatus#DRAFT}.
     * Validated by {@code ck_category_form_versions_status}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "form_status", nullable = false, length = 20)
    private FormStatus formStatus = FormStatus.DRAFT;

    /**
     * Narrow reference to the user who created this form version.
     * Nullable — may be absent for system-created drafts.
     */
    @Column(name = "created_by")
    private Long createdById;

    /**
     * Narrow reference to the user who published this form version.
     * Null while the form is in {@link FormStatus#DRAFT}.
     */
    @Column(name = "published_by")
    private Long publishedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Timestamp of publication.
     * Null for DRAFT; non-null for PUBLISHED and ARCHIVED.
     * Validated by {@code ck_category_form_versions_publish_metadata}.
     */
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /**
     * Whether reporters submitting through this form must attach at least one asset
     * that qualifies as equipment (e.g. a device). Added in V6.
     * Default: {@code false}.
     */
    @Column(name = "requires_equipment", nullable = false)
    private boolean requiresEquipment = false;

    /**
     * Minimum number of file attachments the reporter must upload.
     * {@code 0} means no attachments required.
     * Validated by {@code ck_category_form_versions_min_attachments} ({@code >= 0}).
     * Added in V6.
     */
    @Column(name = "min_attachment_count", nullable = false)
    private int minAttachmentCount = 0;

    /**
     * Narrow reference to the default skill required to handle tickets submitted
     * through this form. Nullable. Added in V6.
     * Contract with M1-06 — stored only as {@code skills.id}; no JPA association.
     */
    @Column(name = "default_skill_id")
    private Long defaultSkillId;

    /**
     * Narrow reference to the SLA policy applied to tickets submitted through
     * this form. Nullable. Added in V6.
     * This is the authoritative SLA reference for ticket-creation logic in later
     * milestones. Stored as {@code sla_policies.id}; no JPA association.
     */
    @Column(name = "default_sla_policy_id")
    private Long defaultSlaPolicyId;

    /** Required by JPA — not for direct use. */
    protected CategoryFormVersion() {
    }

    /**
     * Creates a new DRAFT form version for the given category.
     *
     * @param category      owning category; must not be {@code null}
     * @param versionNumber monotonically increasing version within the category
     */
    public CategoryFormVersion(IncidentCategory category, int versionNumber) {
        this.category = Objects.requireNonNull(category, "category is required");
        this.versionNumber = versionNumber;
    }

    // -------------------------------------------------------------------------
    // Mutation methods
    // -------------------------------------------------------------------------

    /** Sets the narrow creator reference. */
    public void changeCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    /** Sets the narrow publisher reference and records the publication timestamp. */
    public void changePublishingMetadata(Long publishedById, OffsetDateTime publishedAt) {
        this.publishedById = publishedById;
        this.publishedAt = publishedAt;
    }

    /** Updates the form status. Transition logic belongs to a publishing service, not here. */
    public void changeFormStatus(FormStatus formStatus) {
        this.formStatus = Objects.requireNonNull(formStatus, "formStatus is required");
    }

    /** Sets whether this form requires equipment. */
    public void changeRequiresEquipment(boolean requiresEquipment) {
        this.requiresEquipment = requiresEquipment;
    }

    /** Sets the minimum attachment count. Must be {@code >= 0}. */
    public void changeMinAttachmentCount(int minAttachmentCount) {
        if (minAttachmentCount < 0) {
            throw new IllegalArgumentException("minAttachmentCount must be >= 0");
        }
        this.minAttachmentCount = minAttachmentCount;
    }

    /** Sets or clears the narrow default skill reference. */
    public void changeDefaultSkillId(Long defaultSkillId) {
        this.defaultSkillId = defaultSkillId;
    }

    /** Sets or clears the narrow default SLA policy reference. */
    public void changeDefaultSlaPolicyId(Long defaultSlaPolicyId) {
        this.defaultSlaPolicyId = defaultSlaPolicyId;
    }

    // -------------------------------------------------------------------------
    // JPA lifecycle — timestamps only; no business validation
    // -------------------------------------------------------------------------

    @PrePersist
    void initializeTimestamps() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors — read-only
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public FormStatus getFormStatus() {
        return formStatus;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public Long getPublishedById() {
        return publishedById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public boolean isRequiresEquipment() {
        return requiresEquipment;
    }

    public int getMinAttachmentCount() {
        return minAttachmentCount;
    }

    public Long getDefaultSkillId() {
        return defaultSkillId;
    }

    public Long getDefaultSlaPolicyId() {
        return defaultSlaPolicyId;
    }

    @Override
    public String toString() {
        return "CategoryFormVersion{id=" + id + ", categoryId="
                + (category != null ? category.getId() : null)
                + ", versionNumber=" + versionNumber + ", formStatus=" + formStatus + "}";
    }
}
