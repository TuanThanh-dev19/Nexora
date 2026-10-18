package com.nexora.dashboard.application;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import com.nexora.dashboard.dto.DashboardSummaryResponse;
import com.nexora.dashboard.port.DashboardSummaryQuery;

@Service
public class DefaultDashboardSummaryService implements DashboardSummaryService {
    private final DashboardSummaryQuery query;
    public DefaultDashboardSummaryService(DashboardSummaryQuery query) { this.query = query; }
    @Override
    public DashboardSummaryResponse getSummary(DashboardViewer viewer) {
        if (viewer.roles().isEmpty()) throw new AccessDeniedException("No dashboard read role.");
        return query.query(viewer);
    }
}
