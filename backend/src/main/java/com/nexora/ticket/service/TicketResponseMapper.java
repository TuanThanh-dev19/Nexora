package com.nexora.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.FieldDefinition;
import com.nexora.category.entity.FieldOption;
import com.nexora.ticket.dto.response.TicketDetailResponse;
import com.nexora.ticket.dto.response.TicketDetailResponse.AttachmentMetadata;
import com.nexora.ticket.dto.response.TicketDetailResponse.DynamicValue;
import com.nexora.ticket.dto.response.TicketDetailResponse.Field;
import com.nexora.ticket.dto.response.TicketDetailResponse.HistoryEntry;
import com.nexora.ticket.dto.response.TicketDetailResponse.Option;
import com.nexora.ticket.dto.response.TicketDetailResponse.StoredForm;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse.AssetReference;
import com.nexora.ticket.dto.response.TicketSummaryResponse.CatalogReference;
import com.nexora.ticket.dto.response.TicketSummaryResponse.UserReference;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.dto.response.TicketActionResultResponse.CurrentAssignmentResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse.TechnicianSummary;
import com.nexora.ticket.dto.response.TicketActionResultResponse.UserSummary;
import com.nexora.ticket.entity.TicketAttachment;
import com.nexora.ticket.entity.TicketFieldValue;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.user.entity.AppUser;

/** Maps only explicitly public data; call within a transaction. */
@Component
public class TicketResponseMapper {
    public TicketSummaryResponse summary(Ticket ticket) {
        return summary(ticket, null);
    }

    public TicketSummaryResponse summary(Ticket ticket, CurrentAssignmentResponse currentAssignment) {
        return new TicketSummaryResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getTitle(),
                ticket.getStatus(), ticket.getPriority(),
                new CatalogReference(ticket.getCategory().getId(), ticket.getCategory().getCode(), ticket.getCategory().getName()),
                new CatalogReference(ticket.getLocation().getId(), ticket.getLocation().getCode(), ticket.getLocation().getName()),
                ticket.getAsset() == null ? null : new AssetReference(ticket.getAsset().getId(), ticket.getAsset().getAssetCode()),
                user(ticket.getReporter()), ticket.getSlaDueAt(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                currentAssignment);
    }

    public TicketDetailResponse detail(Ticket ticket, List<FieldDefinition> fields, List<FieldOption> options,
            List<TicketFieldValue> values, List<TicketStatusHistory> history, List<TicketAttachment> attachments,
            CurrentAssignmentResponse currentAssignment, List<CurrentAssignmentResponse> assignmentHistory) {
        TicketSummaryResponse summary = summary(ticket);
        StoredForm storedForm = storedForm(ticket.getFormVersion(), fields, options);
        List<DynamicValue> dynamicValues = values.stream()
                .map(value -> new DynamicValue(value.getFieldDefinition().getId(), value.getValueJson())).toList();
        List<HistoryEntry> statusHistory = history.stream()
                .map(entry -> new HistoryEntry(entry.getId(), entry.getFromStatus(), entry.getToStatus(),
                        entry.getTransitionAction(), entry.getReason(), user(entry.getChangedBy()), entry.getChangedAt())).toList();
        List<AttachmentMetadata> attachmentMetadata = attachments.stream()
                .map(this::attachment).toList();

        return new TicketDetailResponse(summary.id(), summary.ticketNumber(), summary.title(), summary.status(),
                summary.priority(), summary.category(), summary.location(), summary.asset(), summary.reporter(),
                summary.slaDueAt(), summary.createdAt(), summary.updatedAt(), ticket.getDescription(), ticket.getVersion(),
                storedForm, dynamicValues, statusHistory, attachmentMetadata, currentAssignment, assignmentHistory,
                ticket.getResolutionOutcome(), ticket.getResolutionSummary(), ticket.getResolvedAt(), ticket.getClosedAt());
    }

    public AttachmentMetadata attachment(TicketAttachment attachment) {
        return new AttachmentMetadata(attachment.getId(),
                attachment.getFieldDefinition() == null ? null : attachment.getFieldDefinition().getId(),
                attachment.getAttachmentType(), attachment.getOriginalName(), attachment.getContentType(),
                attachment.getSizeBytes(), user(attachment.getUploadedBy()), attachment.getCreatedAt());
    }

    public com.nexora.ticket.dto.response.WorkLogResponse workLog(com.nexora.ticket.entity.WorkLog log) {
        return new com.nexora.ticket.dto.response.WorkLogResponse(
                log.getId(),
                log.getTicket().getId(),
                new com.nexora.ticket.dto.response.WorkLogResponse.TechnicianSummary(
                        log.getTechnician().getId(), log.getTechnician().getFullName()),
                log.getLogType(),
                log.getWorkDescription(),
                log.getStartedAt(),
                log.getEndedAt(),
                log.getMinutesSpent(),
                log.getCreatedAt());
    }

    public com.nexora.ticket.dto.response.WorkLogCreatedResponse workLogCreated(
            com.nexora.ticket.entity.WorkLog log, Long ticketVersion) {
        return new com.nexora.ticket.dto.response.WorkLogCreatedResponse(
                log.getId(),
                log.getTicket().getId(),
                new com.nexora.ticket.dto.response.WorkLogResponse.TechnicianSummary(
                        log.getTechnician().getId(), log.getTechnician().getFullName()),
                log.getLogType(),
                log.getWorkDescription(),
                log.getStartedAt(),
                log.getEndedAt(),
                log.getMinutesSpent(),
                log.getCreatedAt(),
                ticketVersion);
    }

    public CurrentAssignmentResponse assignment(TicketAssignment assignment, AppUser technician, AppUser assignedBy) {
        return new CurrentAssignmentResponse(assignment.getId(), assignment.getTicketId(),
                new TechnicianSummary(technician.getId(), technician.getFullName()),
                new UserSummary(assignedBy.getId(), assignedBy.getFullName()), assignment.getAssignedAt(),
                assignment.getAcceptedAt(), assignment.getEndedAt(), assignment.getEndReason(), assignment.getOverrideReason());
    }

    private StoredForm storedForm(CategoryFormVersion form, List<FieldDefinition> fields, List<FieldOption> options) {
        Map<Long, List<FieldOption>> optionsByField = options.stream().collect(
                Collectors.groupingBy(option -> option.getFieldDefinition().getId()));
        List<Field> storedFields = fields.stream()
                .map(field -> field(field, optionsByField.getOrDefault(field.getId(), List.of()))).toList();
        return new StoredForm(form.getId(), form.getVersionNumber(), form.getFormStatus(), storedFields);
    }

    private Field field(FieldDefinition field, List<FieldOption> options) {
        List<Option> storedOptions = options.stream()
                .map(option -> new Option(option.getOptionValue(), option.getOptionLabel())).toList();
        return new Field(field.getId(), field.getFieldKey(), field.getLabel(), field.getFieldType(),
                field.isRequired(), field.getDisplayOrder(), storedOptions);
    }

    private UserReference user(AppUser user) {
        return new UserReference(user.getId(), user.getFullName());
    }
}
