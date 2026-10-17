package com.nexora.ticket.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.common.domain.Priority;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.InvalidStateTransitionException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.EligibleTechniciansQuery;
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
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.service.TechnicianEligibilityService.EligibilityEvaluation;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Role;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.Skill;
import com.nexora.user.entity.TechnicianProfile;
import com.nexora.user.entity.UserRole;
import com.nexora.user.entity.UserStatus;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.TechnicianProfileRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketAssignmentServiceTest {

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

    private TicketAssignmentService assignmentService;

    private Jwt jwt;
    private Ticket ticket;
    private AppUser managerUser;
    private AppUser technicianUser;
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
                eligibilityService);

        jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "10")
                .build();

        Location campus = new Location("CAMPUS-1", "Main Campus", LocationType.CAMPUS, null);
        ReflectionTestUtils.setField(campus, "id", 1L);

        Skill skill = new Skill("ELEC", "Electrical");
        ReflectionTestUtils.setField(skill, "id", 10L);

        AppUser reporter = new AppUser("user@nexora.local", "hash", "Reporter", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(reporter, "id", 1L);

        IncidentCategory category = new IncidentCategory("CAT", "Category");
        ReflectionTestUtils.setField(category, "id", 2L);
        CategoryFormVersion formVersion = new CategoryFormVersion(category, 1);
        ReflectionTestUtils.setField(formVersion, "id", 100L);

        ticket = new Ticket(
                "TCK-001",
                reporter,
                category,
                formVersion,
                campus,
                null,
                "Title",
                "Desc",
                Priority.NORMAL,
                null,
                skill);
        ReflectionTestUtils.setField(ticket, "id", 100L);
        ReflectionTestUtils.setField(ticket, "version", 0L);
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.UNDER_REVIEW);

        managerUser = new AppUser("manager@nexora.local", "hash", "Manager User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(managerUser, "id", 10L);
        Role managerRole = new Role(RoleCode.MANAGER, "Manager");
        ReflectionTestUtils.setField(managerRole, "id", (short) 3);
        UserRole mgrUr = new UserRole(managerUser, managerRole, null);
        ReflectionTestUtils.setField(managerUser, "roleAssignments", List.of(mgrUr));

        technicianUser = new AppUser("tech@nexora.local", "hash", "Tech User", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(technicianUser, "id", 20L);
        Role techRole = new Role(RoleCode.TECHNICIAN, "Technician");
        ReflectionTestUtils.setField(techRole, "id", (short) 2);
        UserRole techUr = new UserRole(technicianUser, techRole, null);
        ReflectionTestUtils.setField(technicianUser, "roleAssignments", List.of(techUr));

        technicianProfile = new TechnicianProfile(technicianUser);
        ReflectionTestUtils.setField(technicianProfile, "maxActiveTickets", 5);
        ReflectionTestUtils.setField(technicianProfile, "available", true);
    }

    @Test
    @DisplayName("eligibleTechnicians: Manager xem danh sách candidate thành công")
    void eligibleTechnicians_manager_success() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.of(ticket));

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(PageRequest.of(0, 10), null, null);
        PageResponse<EligibleTechnicianResponse> expectedPage = new PageResponse<>(List.of(), 0, 10, 0, 0);
        given(eligibilityService.findEligibleTechnicians(ticket, query)).willReturn(expectedPage);

        PageResponse<EligibleTechnicianResponse> result = assignmentService.eligibleTechnicians(jwt, 100L, query);

        assertThat(result).isSameAs(expectedPage);
    }

    @Test
    @DisplayName("eligibleTechnicians: User không có quyền Manager/Admin bị AccessDeniedException (HTTP 403)")
    void eligibleTechnicians_nonManager_throwsAccessDeniedException() {
        CurrentUserResponse userResponse = new CurrentUserResponse(
                1L, "user@nexora.local", "User", "ACTIVE", List.of("REQUESTER"));
        given(authService.getCurrentUser(jwt)).willReturn(userResponse);

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(PageRequest.of(0, 10), null, null);

        assertThatThrownBy(() -> assignmentService.eligibleTechnicians(jwt, 100L, query))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("eligibleTechnicians: Ticket không tồn tại hoặc không thể truy cập ném ResourceNotFoundException (HTTP 404)")
    void eligibleTechnicians_ticketNotFound_throwsResourceNotFoundException() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.empty());

        EligibleTechniciansQuery query = new EligibleTechniciansQuery(PageRequest.of(0, 10), null, null);

        assertThatThrownBy(() -> assignmentService.eligibleTechnicians(jwt, 999L, query))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assign: Phân công thành công cho technician đủ điều kiện")
    void assign_eligibleTechnician_success() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndEndedAtIsNull(100L)).willReturn(false);
        given(profileRepository.findByIdForUpdate(20L)).willReturn(Optional.of(technicianProfile));

        EligibilityEvaluation evaluation = new EligibilityEvaluation(technicianUser, technicianProfile, List.of());
        given(eligibilityService.evaluateCandidate(ticket, 20L)).willReturn(evaluation);
        given(userRepository.findById(10L)).willReturn(Optional.of(managerUser));

        TicketAssignment savedAssignment = new TicketAssignment(
                100L, 20L, 10L, OffsetDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(savedAssignment, "id", 1000L);
        given(assignmentRepository.save(any(TicketAssignment.class))).willReturn(savedAssignment);
        given(ticketRepository.save(any(Ticket.class))).willReturn(ticket);

        AssignTicketRequest request = new AssignTicketRequest(0L, 20L);

        TicketActionResultResponse response = assignmentService.assign(jwt, 100L, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(response.currentAssignment()).isNotNull();
        assertThat(response.currentAssignment().technician().id()).isEqualTo(20L);

        verify(profileRepository).findByIdForUpdate(20L);
        verify(statusHistoryRepository).save(any(TicketStatusHistory.class));
    }

    @Test
    @DisplayName("assign: Từ chối phân công khi technician không đủ điều kiện (HTTP 422)")
    void assign_ineligibleTechnician_throwsBusinessRuleException() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndEndedAtIsNull(100L)).willReturn(false);
        given(profileRepository.findByIdForUpdate(20L)).willReturn(Optional.of(technicianProfile));

        EligibilityEvaluation evaluation = new EligibilityEvaluation(
                technicianUser, technicianProfile, List.of(new EligibilityReason("CAPACITY_REACHED", false)));
        given(eligibilityService.evaluateCandidate(ticket, 20L)).willReturn(evaluation);

        AssignTicketRequest request = new AssignTicketRequest(0L, 20L);

        assertThatThrownBy(() -> assignmentService.assign(jwt, 100L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CAPACITY_REACHED");

        verify(assignmentRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: Xung đột phiên bản (Stale write) ném StaleWriteException (HTTP 409)")
    void assign_staleVersion_throwsStaleWriteException() {
        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.of(ticket));
        given(assignmentRepository.existsByTicketIdAndEndedAtIsNull(100L)).willReturn(false);

        AssignTicketRequest request = new AssignTicketRequest(999L, 20L); // Version mismatch

        assertThatThrownBy(() -> assignmentService.assign(jwt, 100L, request))
                .isInstanceOf(StaleWriteException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: Ticket ở trạng thái không thể phân công (ví dụ CLOSED) ném InvalidStateTransitionException")
    void assign_invalidStatus_throwsInvalidStateTransitionException() {
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.CLOSED);

        CurrentUserResponse managerResponse = new CurrentUserResponse(
                10L, "manager@nexora.local", "Manager User", "ACTIVE", List.of("MANAGER"));
        given(authService.getCurrentUser(jwt)).willReturn(managerResponse);
        given(ticketRepository.findOne(any(Specification.class))).willReturn(Optional.of(ticket));

        AssignTicketRequest request = new AssignTicketRequest(0L, 20L);

        assertThatThrownBy(() -> assignmentService.assign(jwt, 100L, request))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(assignmentRepository, never()).save(any());
    }
}
