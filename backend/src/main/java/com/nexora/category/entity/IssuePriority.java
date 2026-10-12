package com.nexora.category.entity;

/**
 * Priority levels shared by incident categories and SLA policies.
 *
 * <p>Values must match the DB check constraints:
 * <ul>
 *   <li>{@code ck_incident_categories_priority} — {@code dbo.incident_categories.default_priority}</li>
 *   <li>{@code ck_sla_policies_priority}         — {@code dbo.sla_policies.priority}</li>
 * </ul>
 */
public enum IssuePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
