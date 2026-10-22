package com.nexora.ticket.dto.response;

import java.time.OffsetDateTime;

import com.nexora.ticket.dto.response.WorkLogResponse.TechnicianSummary;
import com.nexora.ticket.entity.WorkLogType;

public record WorkLogCreatedResponse(
        Long id,
        Long ticketId,
        TechnicianSummary technician,
        WorkLogType logType,
        String content,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        Integer minutesSpent,
        OffsetDateTime createdAt,
        Long ticketVersion) {}
