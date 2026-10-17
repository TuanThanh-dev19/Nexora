package com.nexora.ticket.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
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
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse.CurrentAssignmentResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse.TechnicianSummary;
import com.nexora.ticket.dto.response.TicketActionResultResponse.UserSummary;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketSpecifications;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.service.TechnicianEligibilityService.EligibilityEvaluation;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.TechnicianProfileRepository;

@Service
@Transactional
public class TicketAssignmentService {

    private final AuthService authService;
    private final TicketRepository ticketRepository;
    private final AppUserRepository userRepository;
    private final TechnicianProfileRepository profileRepository;
    private final TicketAssignmentRepository assignmentRepository;
    private final TicketStatusHistoryRepository statusHistoryRepository;
    private final TechnicianEligibilityService eligibilityService;

    public TicketAssignmentService(
            AuthService authService,
            TicketRepository ticketRepository,
            AppUserRepository userRepository,
            TechnicianProfileRepository profileRepository,
            TicketAssignmentRepository assignmentRepository,
            TicketStatusHistoryRepository statusHistoryRepository,
            TechnicianEligibilityService eligibilityService) {
        this.authService = authService;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.eligibilityService = eligibilityService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EligibleTechnicianResponse> eligibleTechnicians(
            Jwt jwt, Long ticketId, EligibleTechniciansQuery query) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        requireManagerOrAdmin(roles);

        Ticket ticket = ticketRepository.findOne(
                TicketSpecifications.visibleTo(viewer.id(), roles)
                        .and(TicketSpecifications.id(ticketId)))
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        return eligibilityService.findEligibleTechnicians(ticket, query);
    }

    public TicketActionResultResponse assign(Jwt jwt, Long ticketId, AssignTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        requireManagerOrAdmin(roles);

        Ticket ticket = ticketRepository.findOne(
                TicketSpecifications.visibleTo(viewer.id(), roles)
                        .and(TicketSpecifications.id(ticketId)))
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        TicketWorkflowContract.requireTransition(ticket.getStatus(), TicketAction.ASSIGN);

        if (assignmentRepository.existsByTicketIdAndEndedAtIsNull(ticketId)) {
            throw new BusinessRuleException("Ticket already has an active assignment.");
        }

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new StaleWriteException();
        }

        profileRepository.findByIdForUpdate(request.technicianId());

        EligibilityEvaluation evaluation = eligibilityService.evaluateCandidate(ticket, request.technicianId());
        if (!evaluation.isEligible()) {
            String reasons = evaluation.reasons().stream()
                    .map(EligibilityReason::code)
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleException("Technician is not eligible: " + reasons);
        }

        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TicketAssignment assignment = new TicketAssignment(
                ticket.getId(),
                request.technicianId(),
                actor.getId(),
                now);
        assignment = assignmentRepository.save(assignment);

        TicketStatusHistory history = new TicketStatusHistory(
                ticket,
                ticket.getStatus(),
                TicketStatus.ASSIGNED,
                TicketAction.ASSIGN,
                null,
                actor,
                now);
        statusHistoryRepository.save(history);

        ticket.changeStatus(TicketStatus.ASSIGNED);
        ticket = ticketRepository.save(ticket);

        AppUser technician = evaluation.user();
        return new TicketActionResultResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getVersion(),
                new CurrentAssignmentResponse(
                        assignment.getId(),
                        ticket.getId(),
                        new TechnicianSummary(technician.getId(), technician.getFullName()),
                        new UserSummary(actor.getId(), actor.getFullName()),
                        assignment.getAssignedAt(),
                        assignment.getAcceptedAt(),
                        assignment.getEndedAt(),
                        assignment.getEndReason(),
                        assignment.getOverrideReason()));
    }

    private CurrentUserResponse getViewer(Jwt jwt) {
        try {
            return authService.getCurrentUser(jwt);
        } catch (InvalidCredentialsException exception) {
            throw new BadCredentialsException("Invalid identity.");
        }
    }

    private Set<RoleCode> getRoles(CurrentUserResponse viewer) {
        Set<RoleCode> roles = Arrays.stream(RoleCode.values())
                .filter(role -> viewer.roles().contains(role.name()))
                .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            throw new AccessDeniedException("No ticket role.");
        }
        return roles;
    }

    private void requireManagerOrAdmin(Set<RoleCode> roles) {
        if (!roles.contains(RoleCode.MANAGER) && !roles.contains(RoleCode.ADMIN)) {
            throw new AccessDeniedException("Only managers and admins can manage ticket assignments.");
        }
    }
}
