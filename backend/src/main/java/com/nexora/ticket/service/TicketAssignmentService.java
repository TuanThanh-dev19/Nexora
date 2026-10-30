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
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.pagination.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.util.MultiValueMap;

import com.nexora.ticket.dto.request.AddWorkLogRequest;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.ReassignTicketRequest;
import com.nexora.ticket.dto.request.CloseTicketRequest;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
import com.nexora.ticket.dto.request.ResolveTicketRequest;
import com.nexora.ticket.dto.request.ReviewTicketRequest;
import com.nexora.ticket.dto.request.StartTicketRequest;
import com.nexora.ticket.dto.request.TechnicianAssignedTicketsQuery;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse.CurrentAssignmentResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.dto.response.WorkLogCreatedResponse;
import com.nexora.ticket.dto.response.WorkLogResponse;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.entity.WorkLog;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketSpecifications;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.repository.WorkLogRepository;
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
    private final WorkLogRepository workLogRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public TicketAssignmentService(
            AuthService authService,
            TicketRepository ticketRepository,
            AppUserRepository userRepository,
            TechnicianProfileRepository profileRepository,
            TicketAssignmentRepository assignmentRepository,
            TicketStatusHistoryRepository statusHistoryRepository,
            TechnicianEligibilityService eligibilityService,
            TicketResponseMapper mapper,
            WorkLogRepository workLogRepository) {
        this.authService = authService;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.eligibilityService = eligibilityService;
        this.mapper = mapper;
        this.workLogRepository = workLogRepository;
    }

    public TicketAssignmentService(
            AuthService authService,
            TicketRepository ticketRepository,
            AppUserRepository userRepository,
            TechnicianProfileRepository profileRepository,
            TicketAssignmentRepository assignmentRepository,
            TicketStatusHistoryRepository statusHistoryRepository,
            TechnicianEligibilityService eligibilityService,
            TicketResponseMapper mapper) {
        this(authService, ticketRepository, userRepository, profileRepository, assignmentRepository,
                statusHistoryRepository, eligibilityService, mapper, null);
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

    public TicketActionResultResponse reassign(Jwt jwt, Long ticketId, ReassignTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        requireManagerOrAdmin(getRoles(viewer));
        Ticket ticket = lockedTicket(ticketId, request.version(), TicketAction.REASSIGN);
        TicketAssignment current = assignmentRepository.findByTicketIdAndEndedAtIsNull(ticketId)
                .orElseThrow(() -> new BusinessRuleException("Ticket has no active assignment."));
        if (Objects.equals(current.getTechnicianId(), request.technicianId())) {
            throw new BusinessRuleException("Choose a different technician for reassignment.");
        }

        // Match assign(): Ticket first, then destination profile to serialize capacity checks.
        profileRepository.findByIdForUpdate(request.technicianId());
        EligibilityEvaluation evaluation = eligibilityService.evaluateCandidateForAssignment(ticket, request.technicianId());
        if (!evaluation.isEligible()) {
            String reasons = evaluation.reasons().stream().map(EligibilityReason::code).collect(Collectors.joining(", "));
            throw new BusinessRuleException("Technician is not eligible: " + reasons);
        }
        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        current.end(now, request.reason());
        // Release the filtered unique-index entry before Hibernate inserts the replacement.
        // This flush is still inside the transaction: later failures also roll back the end.
        assignmentRepository.flush();
        TicketAssignment replacement = assignmentRepository.save(
                new TicketAssignment(ticketId, request.technicianId(), actor.getId(), now));

        // The status stays ASSIGNED, so explicitly dirty the Ticket for one @Version increment.
        ticket.touch(now);
        statusHistoryRepository.save(new TicketStatusHistory(ticket, TicketStatus.ASSIGNED, TicketStatus.ASSIGNED,
                TicketAction.REASSIGN, request.reason(), actor, now));
        ticketRepository.flush();
        return new TicketActionResultResponse(ticketId, ticket.getStatus(), ticket.getVersion(),
                mapper.assignment(replacement, evaluation.user(), actor));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryResponse> assignedTickets(Jwt jwt, TechnicianAssignedTicketsQuery query) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        if (!roles.contains(RoleCode.TECHNICIAN)) {
            throw new AccessDeniedException("Only technicians can access assigned tickets.");
        }

        var spec = TicketSpecifications.currentTechnicianWork(
                viewer.id(), query.status(), query.priority(), query.locationId());

        org.springframework.data.domain.Page<Ticket> page = ticketRepository.findAll(spec, query.pageable());
        if (page.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        List<Long> ticketIds = page.getContent().stream().map(Ticket::getId).toList();
        List<TicketAssignment> activeAssignments = assignmentRepository.findByTicketIdInAndEndedAtIsNull(ticketIds);
        Map<Long, TicketAssignment> assignmentByTicketId = activeAssignments.stream()
                .collect(Collectors.toMap(TicketAssignment::getTicketId, a -> a, (first, second) -> first));

        Set<Long> userIds = activeAssignments.stream()
                .flatMap(a -> Stream.of(a.getTechnicianId(), a.getAssignedBy()))
                .collect(Collectors.toSet());
        Map<Long, AppUser> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> u));

        List<TicketSummaryResponse> content = page.getContent().stream().map(ticket -> {
            TicketAssignment assignment = assignmentByTicketId.get(ticket.getId());
            AppUser tech = userMap.getOrDefault(assignment.getTechnicianId(),
                    userRepository.getReferenceById(assignment.getTechnicianId()));
            AppUser assignedBy = userMap.getOrDefault(assignment.getAssignedBy(),
                    userRepository.getReferenceById(assignment.getAssignedBy()));
            CurrentAssignmentResponse currentAssignment = mapper.assignment(assignment, tech, assignedBy);
            return mapper.summary(ticket, currentAssignment);
        }).toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public TicketActionResultResponse start(Jwt jwt, Long ticketId, StartTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        if (!roles.contains(RoleCode.TECHNICIAN)) {
            throw new AccessDeniedException("Only technicians can start work on a ticket.");
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        boolean isVisible = roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER)
                || assignmentRepository.existsByTicketIdAndTechnicianId(ticketId, viewer.id())
                || (roles.contains(RoleCode.REQUESTER) && ticket.getReporter().getId().equals(viewer.id()));
        if (!isVisible) {
            throw new ResourceNotFoundException("The requested ticket is unavailable.");
        }

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new StaleWriteException();
        }

        if (ticket.getStatus() != TicketStatus.ASSIGNED) {
            throw new BusinessRuleException("Ticket must be in ASSIGNED status to start work.");
        }

        TicketWorkflowContract.requireTransition(ticket.getStatus(), TicketAction.START_WORK);

        TicketAssignment currentAssignment = assignmentRepository.findByTicketIdAndEndedAtIsNull(ticketId)
                .orElseThrow(() -> new BusinessRuleException("Ticket has no active assignment."));

        if (!Objects.equals(currentAssignment.getTechnicianId(), viewer.id())) {
            throw new BusinessRuleException("Only the current assigned technician can start work.");
        }

        if (currentAssignment.getEndedAt() != null) {
            throw new BusinessRuleException("Assignment has already ended.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        TicketStatus from = ticket.getStatus();
        ticket.changeStatus(TicketStatus.IN_PROGRESS);

        currentAssignment.accept(now);

        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));
        statusHistoryRepository.save(new TicketStatusHistory(ticket, from, TicketStatus.IN_PROGRESS,
                TicketAction.START_WORK, null, actor, now));

        ticketRepository.flush();

        AppUser assignedBy = userRepository.findById(currentAssignment.getAssignedBy()).orElse(actor);

        return new TicketActionResultResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getVersion(),
                mapper.assignment(currentAssignment, actor, assignedBy));
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> workLogs(Jwt jwt, Long ticketId, MultiValueMap<String, String> queryParameters) {
        if (queryParameters != null && !queryParameters.isEmpty()) {
            throw new UnsupportedFilterException("Query parameters are not supported for work logs.");
        }
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);

        Ticket ticket = ticketRepository.findOne(
                TicketSpecifications.visibleTo(viewer.id(), roles)
                        .and(TicketSpecifications.id(ticketId)))
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        return workLogRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                .map(mapper::workLog)
                .toList();
    }

    public WorkLogCreatedResponse addWorkLog(Jwt jwt, Long ticketId, AddWorkLogRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        if (!roles.contains(RoleCode.TECHNICIAN)) {
            throw new AccessDeniedException("Only technicians can add work logs.");
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        boolean isVisible = roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER)
                || assignmentRepository.existsByTicketIdAndTechnicianId(ticketId, viewer.id())
                || (roles.contains(RoleCode.REQUESTER) && ticket.getReporter().getId().equals(viewer.id()));
        if (!isVisible) {
            throw new ResourceNotFoundException("The requested ticket is unavailable.");
        }

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new StaleWriteException();
        }

        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Ticket must be in IN_PROGRESS status to add work log.");
        }

        TicketAssignment currentAssignment = assignmentRepository.findByTicketIdAndEndedAtIsNull(ticketId)
                .orElseThrow(() -> new BusinessRuleException("Ticket has no active assignment."));

        if (!Objects.equals(currentAssignment.getTechnicianId(), viewer.id())) {
            throw new BusinessRuleException("Only the current assigned technician can add work logs.");
        }

        AppUser technician = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        WorkLog workLog = new WorkLog(
                ticket,
                technician,
                request.logType(),
                request.content(),
                request.startedAt(),
                request.endedAt(),
                request.minutesSpent(),
                now);
        workLog = workLogRepository.save(workLog);

        ticket.touch(now);
        ticketRepository.flush();

        return mapper.workLogCreated(workLog, ticket.getVersion());
    }

    public TicketActionResultResponse resolve(Jwt jwt, Long ticketId, ResolveTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = getRoles(viewer);
        if (!roles.contains(RoleCode.TECHNICIAN)) {
            throw new AccessDeniedException("Only technicians can resolve tickets.");
        }

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        boolean isVisible = roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.MANAGER)
                || assignmentRepository.existsByTicketIdAndTechnicianId(ticketId, viewer.id())
                || (roles.contains(RoleCode.REQUESTER) && ticket.getReporter().getId().equals(viewer.id()));
        if (!isVisible) {
            throw new ResourceNotFoundException("The requested ticket is unavailable.");
        }

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new StaleWriteException();
        }

        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Ticket must be in IN_PROGRESS status to resolve.");
        }

        TicketWorkflowContract.requireTransition(ticket.getStatus(), TicketAction.RESOLVE);

        TicketAssignment currentAssignment = assignmentRepository.findByTicketIdAndEndedAtIsNull(ticketId)
                .orElseThrow(() -> new BusinessRuleException("Ticket has no active assignment."));

        if (!Objects.equals(currentAssignment.getTechnicianId(), viewer.id())) {
            throw new BusinessRuleException("Only the current assigned technician can resolve the ticket.");
        }

        if (!workLogRepository.existsByTicketId(ticketId)) {
            throw new BusinessRuleException("Ticket must have at least one saved work log before resolving.");
        }

        profileRepository.findByIdForUpdate(viewer.id());

        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        TicketStatus from = ticket.getStatus();
        ticket.resolve(request.resolutionOutcome(), request.resolutionSummary(), now);

        currentAssignment.end(now, "RESOLVED");

        statusHistoryRepository.save(new TicketStatusHistory(ticket, from, TicketStatus.RESOLVED,
                TicketAction.RESOLVE, null, actor, now));

        ticketRepository.flush();

        return new TicketActionResultResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getVersion(),
                null);
    }

    public TicketActionResultResponse close(Jwt jwt, Long ticketId, CloseTicketRequest request) {
        CurrentUserResponse viewer = getViewer(jwt);
        Set<RoleCode> roles = viewer.roles().stream().map(RoleCode::valueOf).collect(Collectors.toSet());

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));

        if (!ticketRepository.exists(TicketSpecifications.visibleTo(viewer.id(), roles)
                .and(TicketSpecifications.id(ticketId)))) {
            throw new ResourceNotFoundException("The requested ticket is unavailable.");
        }

        if (!roles.contains(RoleCode.REQUESTER) || !ticket.getReporter().getId().equals(viewer.id())) {
            throw new AccessDeniedException("Only the reporter with REQUESTER role can close the ticket.");
        }

        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new StaleWriteException();
        }

        TicketWorkflowContract.requireTransition(ticket.getStatus(), TicketAction.CLOSE);
        if (assignmentRepository.existsByTicketIdAndEndedAtIsNull(ticketId)) {
            throw new BusinessRuleException("A resolved ticket cannot have an active assignment.");
        }

        AppUser actor = userRepository.findById(viewer.id())
                .orElseThrow(() -> new BadCredentialsException("Actor identity unavailable."));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        TicketStatus from = ticket.getStatus();

        ticket.close(now);

        String reason = request.reason() != null && !request.reason().trim().isEmpty()
                ? request.reason().trim()
                : null;

        statusHistoryRepository.save(new TicketStatusHistory(ticket, from, TicketStatus.CLOSED,
                TicketAction.CLOSE, reason, actor, now));

        ticketRepository.flush();

        return new TicketActionResultResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getVersion(),
                null);
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
