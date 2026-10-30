package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.notification.event.TicketAssignedNotificationEvent;
import com.nexora.notification.event.TicketResolvedNotificationEvent;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.ResolveTicketRequest;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse.EligibilityReason;
import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.repository.WorkLogRepository;
import com.nexora.ticket.service.TechnicianEligibilityService.EligibilityEvaluation;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.TechnicianProfileRepository;

@ExtendWith(MockitoExtension.class)
class TicketAssignmentNotificationEventTest {

    @Mock
    private AuthService authService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private TechnicianProfileRepository profileRepository;

    @Mock
    private TicketAssignmentRepository assignmentRepository;

    @Mock
    private TicketStatusHistoryRepository statusHistoryRepository;

    @Mock
    private TechnicianEligibilityService eligibilityService;

    @Mock
    private WorkLogRepository workLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TicketAssignmentService assignmentService;

    private Jwt jwt;
    private Ticket ticket;
    private AppUser managerUser;
    private AppUser technicianUser;
    private AppUser reporterUser;
    private TechnicianProfile technicianProfile;

    @BeforeEach
    void setUp() {
        assignmentService = new TicketAssignmentService(
                authService,
                ticketRepository,
                userRepository,
                profileRepository,
                assignmentRepository,
                statusHistoryRepository,
                eligibilityService,
                new TicketResponseMapper(),
                workLogRepository,
                eventPublisher);

        jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "10")
                .build();

        reporterUser = new AppUser("reporter@nexora.local", "hash", "Reporter User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(reporterUser, "id", 99L);

        Location campus = new Location("CAMPUS-1", "Main Campus", LocationType.CAMPUS, null);
        ReflectionTestUtils.setField(campus, "id", 1L);

        IncidentCategory category = new IncidentCategory("CAT-1", "Electrical");
        ReflectionTestUtils.setField(category, "id", 1L);

        CategoryFormVersion formVersion = new CategoryFormVersion(category, 1);
        ReflectionTestUtils.setField(formVersion, "id", 1L);

        ticket = new Ticket("T-20261030-001", reporterUser, category, formVersion, campus, null,
                "AC Broken", "AC is not cooling", Priority.HIGH);
        ReflectionTestUtils.setField(ticket, "id", 100L);
        ReflectionTestUtils.setField(ticket, "version", 0L);
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.UNDER_REVIEW);

        managerUser = new AppUser("manager@nexora.local", "hash", "Manager User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(managerUser, "id", 10L);

        technicianUser = new AppUser("tech@nexora.local", "hash", "Tech User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(technicianUser, "id", 20L);

        technicianProfile = new TechnicianProfile(technicianUser);
        ReflectionTestUtils.setField(technicianProfile, "maxActiveTickets", 5);
        ReflectionTestUtils.setField(technicianProfile, "available", true);
    }

    @Test
    @DisplayName("assign: Publishes TicketAssignedNotificationEvent with correct technicianId and assignmentId")
    void assignPublishesTicketAssignedNotificationEvent() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndEndedAtIsNull(100L)).willReturn(false);
        given(profileRepository.findByIdForUpdate(20L)).willReturn(Optional.of(technicianProfile));

        EligibilityEvaluation evaluation = new EligibilityEvaluation(technicianUser, technicianProfile, List.of());
        given(eligibilityService.evaluateCandidateForAssignment(ticket, 20L)).willReturn(evaluation);
        given(userRepository.findById(10L)).willReturn(Optional.of(managerUser));

        TicketAssignment savedAssignment = new TicketAssignment(
                100L, 20L, 10L, OffsetDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(savedAssignment, "id", 555L);
        given(assignmentRepository.save(any(TicketAssignment.class))).willReturn(savedAssignment);

        AssignTicketRequest request = new AssignTicketRequest(0L, 20L);
        assignmentService.assign(jwt, 100L, request);

        ArgumentCaptor<TicketAssignedNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(TicketAssignedNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TicketAssignedNotificationEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.ticketId()).isEqualTo(100L);
        assertThat(publishedEvent.ticketNumber()).isEqualTo("T-20261030-001");
        assertThat(publishedEvent.ticketTitle()).isEqualTo("AC Broken");
        assertThat(publishedEvent.assignmentId()).isEqualTo(555L);
        assertThat(publishedEvent.technicianId()).isEqualTo(20L);
        assertThat(publishedEvent.assignedByUserId()).isEqualTo(10L);
        assertThat(publishedEvent.eventKey()).isEqualTo("TICKET_ASSIGNED:100:555");
    }

    @Test
    @DisplayName("assign: If assign fails due to ineligible candidate, no event is published")
    void assignFailsDoesNotPublishEvent() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndEndedAtIsNull(100L)).willReturn(false);
        given(profileRepository.findByIdForUpdate(20L)).willReturn(Optional.of(technicianProfile));

        EligibilityEvaluation evaluation = new EligibilityEvaluation(
                technicianUser, technicianProfile, List.of(new EligibilityReason("BLOCKED", false)));
        given(eligibilityService.evaluateCandidateForAssignment(ticket, 20L)).willReturn(evaluation);

        AssignTicketRequest request = new AssignTicketRequest(0L, 20L);

        assertThatThrownBy(() -> assignmentService.assign(jwt, 100L, request))
                .isInstanceOf(BusinessRuleException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("resolve: Publishes TicketResolvedNotificationEvent with correct reporterId and historyId")
    void resolvePublishesTicketResolvedNotificationEvent() {
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.IN_PROGRESS);

        CurrentUserResponse techResponse = new CurrentUserResponse(
                20L, "tech@nexora.local", "Tech User", "ACTIVE", List.of("TECHNICIAN"));
        given(authService.getCurrentUser(jwt)).willReturn(techResponse);
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndTechnicianId(100L, 20L)).willReturn(true);

        TicketAssignment activeAssignment = new TicketAssignment(
                100L, 20L, 10L, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        activeAssignment.accept(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        ReflectionTestUtils.setField(activeAssignment, "id", 555L);
        given(assignmentRepository.findByTicketIdAndEndedAtIsNull(100L)).willReturn(Optional.of(activeAssignment));
        given(workLogRepository.existsByTicketId(100L)).willReturn(true);
        given(profileRepository.findByIdForUpdate(20L)).willReturn(Optional.of(technicianProfile));
        given(userRepository.findById(20L)).willReturn(Optional.of(technicianUser));

        TicketStatusHistory savedHistory = new TicketStatusHistory(ticket, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED,
                com.nexora.ticket.entity.TicketAction.RESOLVE, null, technicianUser);
        ReflectionTestUtils.setField(savedHistory, "id", 777L);
        given(statusHistoryRepository.save(any(TicketStatusHistory.class))).willReturn(savedHistory);

        ResolveTicketRequest request = new ResolveTicketRequest(0L, ResolutionOutcome.FIXED, "Replaced compressor");
        assignmentService.resolve(jwt, 100L, request);

        ArgumentCaptor<TicketResolvedNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(TicketResolvedNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TicketResolvedNotificationEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.ticketId()).isEqualTo(100L);
        assertThat(publishedEvent.ticketNumber()).isEqualTo("T-20261030-001");
        assertThat(publishedEvent.ticketTitle()).isEqualTo("AC Broken");
        assertThat(publishedEvent.historyId()).isEqualTo(777L);
        assertThat(publishedEvent.reporterId()).isEqualTo(99L);
        assertThat(publishedEvent.resolvedByUserId()).isEqualTo(20L);
        assertThat(publishedEvent.outcome()).isEqualTo("FIXED");
        assertThat(publishedEvent.resolutionSummary()).isEqualTo("Replaced compressor");
        assertThat(publishedEvent.eventKey()).isEqualTo("TICKET_RESOLVED:100:777");
    }

    @Test
    @DisplayName("resolve: If resolve fails due to missing work logs, no event is published")
    void resolveFailsDoesNotPublishEvent() {
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.IN_PROGRESS);

        CurrentUserResponse techResponse = new CurrentUserResponse(
                20L, "tech@nexora.local", "Tech User", "ACTIVE", List.of("TECHNICIAN"));
        given(authService.getCurrentUser(jwt)).willReturn(techResponse);
        given(ticketRepository.findByIdForUpdate(100L)).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndTechnicianId(100L, 20L)).willReturn(true);

        TicketAssignment activeAssignment = new TicketAssignment(
                100L, 20L, 10L, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        activeAssignment.accept(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        given(assignmentRepository.findByTicketIdAndEndedAtIsNull(100L)).willReturn(Optional.of(activeAssignment));
        given(workLogRepository.existsByTicketId(100L)).willReturn(false); // No work log!

        ResolveTicketRequest request = new ResolveTicketRequest(0L, ResolutionOutcome.FIXED, "Summary");

        assertThatThrownBy(() -> assignmentService.resolve(jwt, 100L, request))
                .isInstanceOf(BusinessRuleException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
