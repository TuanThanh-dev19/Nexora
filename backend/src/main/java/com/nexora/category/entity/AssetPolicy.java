package com.nexora.category.entity;

/**
 * Determines how an asset must be linked when a reporter submits a ticket
 * under an {@link IncidentCategory}.
 *
 * <p>Values are persisted as VARCHAR and validated by the DB check constraint
 * {@code ck_incident_categories_asset_policy} (V3 migration).
 */
public enum AssetPolicy {

    /** The reporter must select an asset before submitting. */
    REQUIRED,

    /** The reporter may optionally attach an asset. */
    OPTIONAL,

    /** Asset selection is not applicable for this category. */
    FORBIDDEN
}
