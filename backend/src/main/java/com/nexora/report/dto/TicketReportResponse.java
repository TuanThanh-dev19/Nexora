package com.nexora.report.dto;

import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;

public record TicketReportResponse(
        TicketReportSummaryResponse summary,
        PageResponse<TicketSummaryResponse> tickets) {
}

