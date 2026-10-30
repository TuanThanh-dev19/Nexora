package com.nexora.ticket.service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.category.repository.FieldDefinitionRepository;
import com.nexora.category.repository.FieldOptionRepository;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.dto.response.TicketDetailResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketFieldValueRepository;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.repository.TicketAttachmentRepository;
import com.nexora.ticket.repository.TicketSpecifications;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.entity.RoleCode;

@Service
@Transactional(readOnly = true)
public class TicketReadService {
    private final AuthService auth;
    private final TicketRepository tickets;
    private final FieldDefinitionRepository fields;
    private final FieldOptionRepository options;
    private final TicketFieldValueRepository values;
    private final TicketStatusHistoryRepository history;
    private final TicketAttachmentRepository attachments;
    private final TicketResponseMapper mapper;
    private final TicketAssignmentRepository assignments;
    private final AppUserRepository users;

    public TicketReadService(AuthService auth, TicketRepository tickets, FieldDefinitionRepository fields,
            FieldOptionRepository options, TicketFieldValueRepository values, TicketStatusHistoryRepository history,
            TicketAttachmentRepository attachments, TicketResponseMapper mapper,
            TicketAssignmentRepository assignments, AppUserRepository users) {
        this.auth = auth;
        this.tickets = tickets;
        this.fields = fields;
        this.options = options;
        this.values = values;
        this.history = history;
        this.attachments = attachments;
        this.mapper = mapper;
        this.assignments = assignments;
        this.users = users;
    }

    public PageResponse<TicketSummaryResponse> list(Jwt jwt, TicketListQuery query) {
        CurrentUserResponse viewer = viewer(jwt);
        var scope = TicketSpecifications.visibleTo(viewer.id(), roles(viewer));
        return PageResponse.from(tickets.findAll(scope.and(TicketSpecifications.filters(query)), query.pageable())
                .map(mapper::summary));
    }

    public TicketDetailResponse detail(Jwt jwt, Long ticketId) {
        CurrentUserResponse viewer = viewer(jwt);
        var ticket = tickets.findOne(TicketSpecifications.visibleTo(viewer.id(), roles(viewer))
                .and(TicketSpecifications.id(ticketId))).orElseThrow(() ->
                        new ResourceNotFoundException("The requested ticket is unavailable."));
        Long formVersionId = ticket.getFormVersion().getId();
        var storedFields = fields.findByFormVersionIdOrderByDisplayOrderAscIdAsc(formVersionId);
        var storedOptions = options.findByFieldDefinitionFormVersionIdOrderByDisplayOrderAscIdAsc(formVersionId);
        var dynamicValues = values.findByTicketIdOrderByFieldOrder(ticketId);
        var statusHistory = history.findByTicketIdOrderByChangedAtAscIdAsc(ticketId);
        var attachmentMetadata = attachments.findByTicketIdAndAttachmentTypeInOrderByCreatedAtAscIdAsc(ticketId,
                List.of(AttachmentType.REPORT_EVIDENCE, AttachmentType.WORK_RESULT));
        var assignmentHistory = assignments.findByTicketIdOrderByAssignedAtDescIdDesc(ticketId).stream()
                .map(assignment -> mapper.assignment(assignment, users.getReferenceById(assignment.getTechnicianId()),
                        users.getReferenceById(assignment.getAssignedBy())))
                .toList();
        var currentAssignment = assignmentHistory.stream().filter(assignment -> assignment.endedAt() == null)
                .findFirst().orElse(null);
        return mapper.detail(ticket, storedFields, storedOptions, dynamicValues, statusHistory, attachmentMetadata,
                currentAssignment, assignmentHistory);
    }

    private CurrentUserResponse viewer(Jwt jwt) {
        try {
            return auth.getCurrentUser(jwt);
        } catch (InvalidCredentialsException exception) {
            throw new BadCredentialsException("Invalid identity.");
        }
    }

    private Set<RoleCode> roles(CurrentUserResponse viewer) {
        Set<RoleCode> roles = Arrays.stream(RoleCode.values())
                .filter(role -> viewer.roles().contains(role.name())).collect(Collectors.toSet());
        if (roles.isEmpty()) throw new AccessDeniedException("No ticket read role.");
        return roles;
    }
}
