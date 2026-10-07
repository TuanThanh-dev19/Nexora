package com.nexora.ticket.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

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
import com.nexora.ticket.entity.TicketAttachment;
import com.nexora.ticket.entity.TicketFieldValue;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.user.entity.AppUser;

/** Maps only explicitly public data; call within a read transaction. */
@Component
public class TicketResponseMapper {
    public TicketSummaryResponse summary(Ticket ticket) {
        return new TicketSummaryResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getTitle(),
                ticket.getStatus(), ticket.getPriority(),
                new CatalogReference(ticket.getCategory().getId(), ticket.getCategory().getCode(), ticket.getCategory().getName()),
                new CatalogReference(ticket.getLocation().getId(), ticket.getLocation().getCode(), ticket.getLocation().getName()),
                ticket.getAsset() == null ? null : new AssetReference(ticket.getAsset().getId(), ticket.getAsset().getAssetCode()),
                user(ticket.getReporter()), ticket.getSlaDueAt(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    public TicketDetailResponse detail(Ticket ticket, List<FieldDefinition> fields, List<FieldOption> options,
            List<TicketFieldValue> values, List<TicketStatusHistory> history, List<TicketAttachment> attachments) {
        TicketSummaryResponse summary = summary(ticket);
        Map<Long, List<FieldOption>> byField = options.stream().collect(
                Collectors.groupingBy(option -> option.getFieldDefinition().getId()));
        var form = ticket.getFormVersion();
        return new TicketDetailResponse(summary.id(), summary.ticketNumber(), summary.title(), summary.status(),
                summary.priority(), summary.category(), summary.location(), summary.asset(), summary.reporter(),
                summary.slaDueAt(), summary.createdAt(), summary.updatedAt(), ticket.getDescription(), ticket.getVersion(),
                new StoredForm(form.getId(), form.getVersionNumber(), form.getFormStatus(), fields.stream().map(field ->
                        new Field(field.getId(), field.getFieldKey(), field.getLabel(), field.getFieldType(),
                                field.isRequired(), field.getDisplayOrder(), byField.getOrDefault(field.getId(), List.of())
                                        .stream().map(option -> new Option(option.getOptionValue(), option.getOptionLabel())).toList())).toList()),
                values.stream().map(value -> new DynamicValue(value.getFieldDefinition().getId(), value.getValueJson())).toList(),
                history.stream().map(entry -> new HistoryEntry(entry.getId(), entry.getFromStatus(), entry.getToStatus(),
                        entry.getTransitionAction(), entry.getReason(), user(entry.getChangedBy()), entry.getChangedAt())).toList(),
                attachments.stream().map(attachment -> new AttachmentMetadata(attachment.getId(),
                        attachment.getFieldDefinition() == null ? null : attachment.getFieldDefinition().getId(),
                        attachment.getAttachmentType(), attachment.getOriginalName(), attachment.getContentType(),
                        attachment.getSizeBytes(), user(attachment.getUploadedBy()), attachment.getCreatedAt())).toList());
    }

    private UserReference user(AppUser user) {
        return new UserReference(user.getId(), user.getFullName());
    }
}
