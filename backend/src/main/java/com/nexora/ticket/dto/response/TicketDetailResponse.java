package com.nexora.ticket.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import com.nexora.ticket.dto.response.TicketActionResultResponse.CurrentAssignmentResponse;

import com.nexora.category.entity.DynamicFieldType;
import com.nexora.category.entity.FormStatus;
import com.nexora.common.domain.Priority;
import com.nexora.ticket.dto.response.TicketSummaryResponse.AssetReference;
import com.nexora.ticket.dto.response.TicketSummaryResponse.CatalogReference;
import com.nexora.ticket.dto.response.TicketSummaryResponse.UserReference;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketStatus;

public record TicketDetailResponse(
        Long id, String ticketNumber, String title, TicketStatus status, Priority priority,
        CatalogReference category, CatalogReference location, AssetReference asset,
        UserReference reporter, OffsetDateTime slaDueAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        String description, Long version, StoredForm formVersion, List<DynamicValue> dynamicValues,
        List<HistoryEntry> statusHistory, List<AttachmentMetadata> attachments,
        CurrentAssignmentResponse currentAssignment,
        ResolutionOutcome resolutionOutcome,
        String resolutionSummary,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt) {
    public TicketDetailResponse {
        dynamicValues = List.copyOf(dynamicValues);
        statusHistory = List.copyOf(statusHistory);
        attachments = List.copyOf(attachments);
    }
    public record StoredForm(Long id, int versionNumber, FormStatus formStatus, List<Field> fields) {
        public StoredForm { fields = List.copyOf(fields); }
    }
    public record Field(Long id, String fieldKey, String label, DynamicFieldType fieldType,
            boolean required, int displayOrder, List<Option> options) {
        public Field { options = List.copyOf(options); }
    }
    public record Option(String value, String label) {}
    public record DynamicValue(Long fieldDefinitionId, String valueJson) {}
    public record HistoryEntry(Long id, TicketStatus fromStatus, TicketStatus toStatus,
            TicketAction transitionAction, String reason, UserReference changedBy, OffsetDateTime changedAt) {}
    public record AttachmentMetadata(Long id, Long fieldDefinitionId, AttachmentType attachmentType,
            String originalName, String contentType, long sizeBytes, UserReference uploadedBy, OffsetDateTime createdAt) {}
}
