package com.nexora.ticket.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import com.nexora.ticket.dto.request.ReviewTicketRequest;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
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
    private final TicketResponseMapper mapper;

    public TicketAssignmentService(
            AuthService authService,
            TicketRepository ticketRepository,
            AppUserRepository userRepository,
            TechnicianProfileRepository profileRepository,
            TicketAssignmentRepository assignmentRepository,
            TicketStatusHistoryRepository statusHistoryRepository,
            TechnicianEligibilityService eligibilityService, TicketResponseMapper mapper) {
        this.authService = authService;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.eligibilityService = eligibilityService;
        this.mapper = mapper;
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

    public TicketActionResultResponse review(Jwt jwt, Long ticketId, ReviewTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        requireManagerOrAdmin(getRoles(viewer));
        Ticket ticket = lockedTicket(ticketId, request.version(), TicketAction.BEGIN_REVIEW);
        AppUser actor = userRepository.getReferenceById(viewer.id());
        recordTransition(ticket, TicketAction.BEGIN_REVIEW, actor, OffsetDateTime.now(ZoneOffset.UTC));
        ticketRepository.flush();
        return new TicketActionResultResponse(ticket.getId(), ticket.getStatus(), ticket.getVersion(), null);
    }

    public TicketActionResultResponse assign(Jwt jwt, Long ticketId, AssignTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        requireManagerOrAdmin(getRoles(viewer));
        Ticket ticket = lockedTicket(ticketId, request.version(), TicketAction.ASSIGN);

        if (assignmentRepository.existsByTicketIdAndEndedAtIsNull(ticketId)) {
            throw new BusinessRuleException("Ticket already has an active assignment.");
        }

        // Every assignment/configuration writer acquires Ticket before profile when both are needed.
        profileRepository.findByIdForUpdate(request.technicianId());

        EligibilityEvaluation evaluation = eligibilityService.evaluateCandidateForAssignment(ticket, request.technicianId());
        if (!evaluation.isEligible()) {
            String reasons = evaluation.reasons().stream()
                    .map(EligibilityReason::code)
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleException("Technician is not eligible: " + reasons);
        }

        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));

        // Microsecond precision round-trips on both supported persistence test databases.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        TicketAssignment assignment = new TicketAssignment(
                ticket.getId(),
                request.technicianId(),
                actor.getId(),
                now);
        assignment = assignmentRepository.save(assignment);

        recordTransition(ticket, TicketAction.ASSIGN, actor, now);
        // JPA increments @Version exactly once. Map only after that update has executed.
        ticketRepository.flush();
        return new TicketActionResultResponse(ticket.getId(), ticket.getStatus(), ticket.getVersion(),
                mapper.assignment(assignment, evaluation.user(), actor));
    }

    private Ticket lockedTicket(Long ticketId, Long version, TicketAction action) {
        // MANAGER/ADMIN have full visibility under TicketSpecifications.visibleTo.
        // Do not preload the entity: the lock must read the previous writer's committed state.
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));
        if (!Objects.equals(ticket.getVersion(), version)) throw new StaleWriteException();
        TicketWorkflowContract.requireTransition(ticket.getStatus(), action);
        return ticket;
    }

    private void recordTransition(Ticket ticket, TicketAction action, AppUser actor, OffsetDateTime at) {
        TicketStatus from = ticket.getStatus();
        ticket.changeStatus(TicketWorkflowContract.requireTransition(from, action).targetStatus());
        statusHistoryRepository.save(new TicketStatusHistory(ticket, from, ticket.getStatus(), action, null, actor, at));
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
        if (TicketWorkflowContract.definitionFor(TicketAction.ASSIGN).permittedActorRoles().stream()
                .noneMatch(roles::contains)) {
            throw new AccessDeniedException("Only managers and admins can manage ticket assignments.");
        }
    }
}
