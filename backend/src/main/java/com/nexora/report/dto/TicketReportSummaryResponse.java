package com.nexora.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import com.nexora.ticket.entity.TicketStatus;

public record TicketReportSummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String timeZone,
        OffsetDateTime generatedAt,
        long totalTickets,
        long overdueTickets,
        Map<TicketStatus, Long> statusCounts) {
}

