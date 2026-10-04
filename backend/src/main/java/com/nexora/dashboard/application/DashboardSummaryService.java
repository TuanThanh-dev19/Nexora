package com.nexora.dashboard.application;

import com.nexora.dashboard.dto.DashboardSummaryResponse;

public interface DashboardSummaryService {

    DashboardSummaryResponse getSummary(DashboardViewer viewer);
}
