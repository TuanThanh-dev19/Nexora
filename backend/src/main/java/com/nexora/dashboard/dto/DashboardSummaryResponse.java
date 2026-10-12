package com.nexora.dashboard.dto;

import java.time.OffsetDateTime;

/**
 * Contract reserved for {@code GET /api/v1/dashboard/summary}.
 *
 * <p>{@code openTickets} excludes terminal and resolved tickets;
 * {@code inProgressTickets} counts {@code IN_PROGRESS}; {@code slaAtRiskTickets}
 * counts active tickets due within two hours or already overdue; and
 * {@code resolvedThisWeek} uses the current ISO week in Asia/Bangkok.
 */
public record DashboardSummaryResponse(
        long openTickets,
        long inProgressTickets,
        long slaAtRiskTickets,
        long resolvedThisWeek,
        OffsetDateTime generatedAt) {
}
