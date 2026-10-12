/**
 * SLA policy persistence.
 *
 * <p>Contains {@link com.nexora.sla.entity.SlaPolicy} and its repository.
 * SLA policies are reference data shared across modules (categories, tickets).
 * Cross-module references store {@code sla_policies.id} as a narrow {@code Long}.
 */
package com.nexora.sla;
