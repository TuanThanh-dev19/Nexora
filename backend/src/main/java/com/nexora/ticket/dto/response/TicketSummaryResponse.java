package com.nexora.ticket.dto.response;

import java.time.OffsetDateTime;
import com.nexora.common.domain.Priority;
import com.nexora.ticket.entity.TicketStatus;

import com.nexora.ticket.dto.response.TicketActionResultResponse.CurrentAssignmentResponse;

public record TicketSummaryResponse(
        Long id, String ticketNumber, String title, TicketStatus status, Priority priority,
        CatalogReference category, CatalogReference location, AssetReference asset,
        UserReference reporter, OffsetDateTime slaDueAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        CurrentAssignmentResponse currentAssignment) {

    public TicketSummaryResponse(
            Long id, String ticketNumber, String title, TicketStatus status, Priority priority,
            CatalogReference category, CatalogReference location, AssetReference asset,
            UserReference reporter, OffsetDateTime slaDueAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, ticketNumber, title, status, priority, category, location, asset, reporter, slaDueAt, createdAt, updatedAt, null);
    }

    public record CatalogReference(Long id, String code, String name) {}
    public record AssetReference(Long id, String assetCode) {}
    public record UserReference(Long id, String fullName) {}
}
