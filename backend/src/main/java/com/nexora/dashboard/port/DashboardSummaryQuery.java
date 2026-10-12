package com.nexora.dashboard.port;

import com.nexora.dashboard.application.DashboardViewer;
import com.nexora.dashboard.dto.DashboardSummaryResponse;

/**
 * Query boundary for the ticket-backed dashboard implementation planned for a later milestone.
 */
public interface DashboardSummaryQuery {

    DashboardSummaryResponse query(DashboardViewer viewer);
}
