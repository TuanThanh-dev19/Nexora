package com.nexora.ticket.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.ticket.dto.request.StartTicketRequest;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.entity.TicketStatusHistory;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketStatusHistoryRepository;
import com.nexora.ticket.service.TicketAssignmentService;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;
import com.nexora.user.entity.TechnicianProfile;

import jakarta.persistence.EntityManager;

import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.WorkLog;
import com.nexora.ticket.entity.WorkLogType;
import com.nexora.ticket.repository.WorkLogRepository;

public abstract class TechnicianWorkApiContract {

    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected ObjectMapper json;
    @Autowired protected TicketAssignmentService assignmentService;
    @Autowired protected TicketRepository ticketRepository;
    @Autowired protected TicketAssignmentRepository assignmentRepository;
    @Autowired protected WorkLogRepository workLogRepository;
    @MockitoSpyBean protected TicketStatusHistoryRepository historyRepository;

    protected TicketReadFixtures f;
    protected Ticket assignedTicket;
    protected Ticket inProgressTicket;

    @BeforeEach
    void setup() {
        tx(() -> {
            f = new TicketReadFixtures(em).create();

            // Set current ticket to ASSIGNED with active assignment for f.technician
            assignedTicket = f.current;
            assignedTicket.changeStatus(TicketStatus.ASSIGNED);
            em.merge(assignedTicket);

            // Create an IN_PROGRESS ticket for f.technician
            inProgressTicket = f.historical;
            inProgressTicket.changeStatus(TicketStatus.IN_PROGRESS);
            em.merge(inProgressTicket);

            // Active assignment for inProgressTicket
            TicketAssignment activeForHistorical = new TicketAssignment(
                    inProgressTicket.getId(), f.technician.getId(), f.manager.getId(), TicketReadFixtures.TIME.plusHours(4));
            em.persist(activeForHistorical);

            em.flush();
        });
    }

    @AfterEach
    void cleanup() {
        if (f == null) return;
        tx(() -> {
            em.createQuery("delete from WorkLog w where w.ticket.id in :ids")
                    .setParameter("ids", f.ticketIds()).executeUpdate();
            em.createQuery("delete from TicketStatusHistory h where h.ticket.id in :ids")
                    .setParameter("ids", f.ticketIds()).executeUpdate();
            em.createQuery("delete from TicketAssignment a where a.ticketId in :ids")
                    .setParameter("ids", f.ticketIds()).executeUpdate();
            f.cleanup();
        });
    }

    protected void tx(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    protected <T> T tx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    protected Jwt token(AppUser user) {
        return Jwt.withTokenValue("test").header("alg", "HS256").subject(user.getId().toString()).build();
    }

    protected ResultActions myWork(AppUser user, String... params) throws Exception {
        var req = get("/api/v1/technicians/me/assigned-tickets").with(jwt().jwt(token(user)));
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mvc.perform(req);
    }

    protected ResultActions startWork(Long ticketId, AppUser user, String body) throws Exception {
        return mvc.perform(post("/api/v1/tickets/{id}/start", ticketId)
                .with(jwt().jwt(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions getWorkLogs(Long ticketId, AppUser user, String... params) throws Exception {
        var req = get("/api/v1/tickets/{id}/work-logs", ticketId).with(jwt().jwt(token(user)));
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return mvc.perform(req);
    }

    protected ResultActions addWorkLog(Long ticketId, AppUser user, String body) throws Exception {
        return mvc.perform(post("/api/v1/tickets/{id}/work-logs", ticketId)
                .with(jwt().jwt(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions resolveTicket(Long ticketId, AppUser user, String body) throws Exception {
        return mvc.perform(post("/api/v1/tickets/{id}/resolve", ticketId)
                .with(jwt().jwt(token(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected ResultActions closeTicket(Long ticketId, AppUser user, String body) throws Exception {
        var req = post("/api/v1/tickets/{id}/close", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (user != null) {
            req = req.with(jwt().jwt(token(user)));
        }
        return mvc.perform(req);
    }

    protected ResultActions getTicketDetail(Long ticketId, AppUser user) throws Exception {
        return mvc.perform(get("/api/v1/tickets/{id}", ticketId)
                .with(jwt().jwt(token(user))));
    }

    // ==========================================
    // 1. My Work API Tests
    // ==========================================

    @Test
    void myWorkReturnsOnlyCurrentAssignmentsOfLoggedInTechnician() throws Exception {
        // Technician sees assignedTicket and inProgressTicket
        var techRes = myWork(f.technician).andExpect(status().isOk()).andReturn();
        JsonNode techData = json.readTree(techRes.getResponse().getContentAsByteArray());
        assertThat(techData.get("totalElements").asInt()).isEqualTo(2);

        // Each ticket in list has currentAssignment populated
        for (JsonNode item : techData.get("content")) {
            assertThat(item.has("currentAssignment")).isTrue();
            JsonNode assignment = item.get("currentAssignment");
            assertThat(assignment.isNull()).isFalse();
            assertThat(assignment.at("/technician/technicianId").asLong()).isEqualTo(f.technician.getId());
            assertThat(assignment.get("endedAt").isNull()).isTrue();
            // No version field in summary
            assertThat(item.has("version")).isFalse();
        }

        // Multi role technician has no active assignment currently
        var multiRes = myWork(f.multi).andExpect(status().isOk()).andReturn();
        JsonNode multiData = json.readTree(multiRes.getResponse().getContentAsByteArray());
        assertThat(multiData.get("totalElements").asInt()).isEqualTo(0);
        assertThat(multiData.get("content").isEmpty()).isTrue();
    }

    @Test
    void myWorkDefaultIncludesAssignedAndInProgress() throws Exception {
        var res = myWork(f.technician).andExpect(status().isOk()).andReturn();
        JsonNode data = json.readTree(res.getResponse().getContentAsByteArray());
        List<String> statuses = List.of(
                data.get("content").get(0).get("status").asText(),
                data.get("content").get(1).get("status").asText());
        assertThat(statuses).containsExactlyInAnyOrder("ASSIGNED", "IN_PROGRESS");
    }

    @Test
    void myWorkFilterByStatusPriorityAndLocation() throws Exception {
        // Filter by status=ASSIGNED
        myWork(f.technician, "status", "ASSIGNED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ASSIGNED"));

        // Filter by status=IN_PROGRESS
        myWork(f.technician, "status", "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"));

        // Filter by priority
        myWork(f.technician, "priority", "HIGH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assignedTicket.getId()));

        // Filter by locationId
        myWork(f.technician, "locationId", f.location.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Combined AND filter
        myWork(f.technician, "status", "ASSIGNED", "priority", "HIGH", "locationId", f.location.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Combined AND filter with no match
        myWork(f.technician, "status", "ASSIGNED", "priority", "LOW")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void myWorkSortingAndPagination() throws Exception {
        // Sort by ticketNumber asc
        myWork(f.technician, "sort", "ticketNumber,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(assignedTicket.getId()))
                .andExpect(jsonPath("$.content[1].id").value(inProgressTicket.getId()));

        // Sort by ticketNumber desc
        myWork(f.technician, "sort", "ticketNumber,desc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(inProgressTicket.getId()))
                .andExpect(jsonPath("$.content[1].id").value(assignedTicket.getId()));

        // Default direction is asc if omitted
        myWork(f.technician, "sort", "ticketNumber")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(assignedTicket.getId()));

        // Pagination page and size
        myWork(f.technician, "page", "0", "size", "1", "sort", "ticketNumber,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        // Out-of-bounds page returns 200 with content=[] and real totalElements
        myWork(f.technician, "page", "10", "size", "20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(10))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void myWorkRejectsInvalidQueryParameters() throws Exception {
        // Unsupported parameter (technicianId, search, etc.)
        myWork(f.technician, "technicianId", "1").andExpect(status().isBadRequest());
        myWork(f.technician, "assigneeId", "1").andExpect(status().isBadRequest());
        myWork(f.technician, "q", "query").andExpect(status().isBadRequest());

        // Blank parameter
        myWork(f.technician, "status", " ").andExpect(status().isBadRequest());
        myWork(f.technician, "page", "").andExpect(status().isBadRequest());

        // Invalid status enum (only ASSIGNED, IN_PROGRESS allowed)
        myWork(f.technician, "status", "SUBMITTED").andExpect(status().isBadRequest());
        myWork(f.technician, "status", "RESOLVED").andExpect(status().isBadRequest());
        myWork(f.technician, "status", "INVALID").andExpect(status().isBadRequest());

        // Invalid priority
        myWork(f.technician, "priority", "INVALID").andExpect(status().isBadRequest());

        // Invalid numbers
        myWork(f.technician, "page", "-1").andExpect(status().isBadRequest());
        myWork(f.technician, "size", "0").andExpect(status().isBadRequest());
        myWork(f.technician, "size", "101").andExpect(status().isBadRequest());
        myWork(f.technician, "locationId", "0").andExpect(status().isBadRequest());
        myWork(f.technician, "locationId", "-5").andExpect(status().isBadRequest());

        // Invalid sort syntax
        myWork(f.technician, "sort", "createdAt,desc,extra").andExpect(status().isBadRequest());
        myWork(f.technician, "sort", "invalidField").andExpect(status().isBadRequest());
        myWork(f.technician, "sort", "createdAt,sideways").andExpect(status().isBadRequest());
    }

    @Test
    void myWorkAuthorizationRequiresTechnicianRole() throws Exception {
        // Unauthenticated -> 401
        mvc.perform(get("/api/v1/technicians/me/assigned-tickets")).andExpect(status().isUnauthorized());

        // Requester -> 403
        myWork(f.requester).andExpect(status().isForbidden());

        // Manager without TECHNICIAN role -> 403
        myWork(f.manager).andExpect(status().isForbidden());

        // Admin without TECHNICIAN role -> 403
        myWork(f.admin).andExpect(status().isForbidden());
    }

    // ==========================================
    // 2. Start Ticket API Tests
    // ==========================================

    @Test
    void startSuccessfulUpdatesStatusAssignmentHistoryAndVersion() throws Exception {
        Long ticketId = assignedTicket.getId();
        Long initialVersion = assignedTicket.getVersion();

        ResultActions res = startWork(ticketId, f.technician, "{\"version\":" + initialVersion + "}")
                .andExpect(status().isOk());

        JsonNode body = json.readTree(res.andReturn().getResponse().getContentAsByteArray());
        assertThat(body.get("id").asLong()).isEqualTo(ticketId);
        assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(body.get("version").asLong()).isEqualTo(initialVersion + 1);

        JsonNode assignment = body.get("currentAssignment");
        assertThat(assignment.at("/technician/technicianId").asLong()).isEqualTo(f.technician.getId());
        assertThat(assignment.get("acceptedAt").isNull()).isFalse();
        OffsetDateTime acceptedAt = OffsetDateTime.parse(assignment.get("acceptedAt").asText());
        assertThat(acceptedAt).isNotNull();

        // Check database persistence
        tx(() -> {
            Ticket updated = em.find(Ticket.class, ticketId);
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);

            TicketAssignment dbAssignment = em.createQuery(
                    "select a from TicketAssignment a where a.ticketId = :id and a.endedAt is null",
                    TicketAssignment.class).setParameter("id", ticketId).getSingleResult();
            assertThat(dbAssignment.getAcceptedAt()).isEqualTo(acceptedAt);

            var histories = em.createQuery(
                    "select h from TicketStatusHistory h where h.ticket.id = :id order by h.id desc",
                    TicketStatusHistory.class).setParameter("id", ticketId).getResultList();
            assertThat(histories).isNotEmpty();
            assertThat(histories.get(0).getTransitionAction()).isEqualTo(TicketAction.START_WORK);
            assertThat(histories.get(0).getFromStatus()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(histories.get(0).getToStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(histories.get(0).getChangedBy().getId()).isEqualTo(f.technician.getId());
        });
    }

    @Test
    void startSecondTimeRejectedWith422() throws Exception {
        Long ticketId = assignedTicket.getId();
        Long version = assignedTicket.getVersion();

        // First start succeeds
        startWork(ticketId, f.technician, "{\"version\":" + version + "}")
                .andExpect(status().isOk());

        // Second start rejected because ticket is already IN_PROGRESS
        startWork(ticketId, f.technician, "{\"version\":" + (version + 1) + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void startTicketNotInAssignedStatusRejectedWith422() throws Exception {
        // inProgressTicket is already IN_PROGRESS
        startWork(inProgressTicket.getId(), f.technician, "{\"version\":" + inProgressTicket.getVersion() + "}")
                .andExpect(status().isUnprocessableEntity());

        // own is SUBMITTED
        startWork(f.own.getId(), f.technician, "{\"version\":0}")
                .andExpect(status().isNotFound()); // not visible to technician
    }

    @Test
    void startByNonCurrentAssigneeRejected() throws Exception {
        // Multi role technician is not current assignee of assignedTicket -> 422
        // First verify multi has visibility (f.current has historical assignment to multi)
        startWork(assignedTicket.getId(), f.multi, "{\"version\":" + assignedTicket.getVersion() + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void startStaleVersionReturns409() throws Exception {
        Long ticketId = assignedTicket.getId();
        startWork(ticketId, f.technician, "{\"version\":999}")
                .andExpect(status().isConflict());
    }

    static Stream<Arguments> invalidStartBodies() {
        return Stream.of(
                Arguments.of("{}", "version"),
                Arguments.of("{\"version\":null}", "version"),
                Arguments.of("{\"version\":-1}", "version"),
                Arguments.of("{\"version\":\"0\"}", "version"),
                Arguments.of("{\"version\":0.5}", "version"),
                Arguments.of("{\"version\":0,\"technicianId\":1}", "technicianId"),
                Arguments.of("{\"version\":0,\"acceptedAt\":\"2026-10-15T09:45:00Z\"}", "acceptedAt"),
                Arguments.of("{\"version\":0,\"status\":\"IN_PROGRESS\"}", "status"));
    }

    @ParameterizedTest
    @MethodSource("invalidStartBodies")
    void startInvalidBodyReturns400(String body, String expectedField) throws Exception {
        startWork(assignedTicket.getId(), f.technician, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + expectedField).exists());
    }

    @Test
    void startAuthorizationEnforced() throws Exception {
        // Unauthenticated -> 401
        mvc.perform(post("/api/v1/tickets/{id}/start", assignedTicket.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isUnauthorized());

        // Non-technician (manager, requester) -> 403
        startWork(assignedTicket.getId(), f.manager, "{\"version\":0}")
                .andExpect(status().isForbidden());
        startWork(assignedTicket.getId(), f.requester, "{\"version\":0}")
                .andExpect(status().isForbidden());

        // Non-existent ticket -> 404
        startWork(999999L, f.technician, "{\"version\":0}")
                .andExpect(status().isNotFound());
    }

    @Test
    void startAtomicityRollsBackWhenHistoryFails() {
        Long ticketId = assignedTicket.getId();
        Long initialVersion = assignedTicket.getVersion();

        doThrow(new DataIntegrityViolationException("Simulated history write error"))
                .when(historyRepository).save(any());

        assertThatThrownBy(() -> assignmentService.start(
                token(f.technician), ticketId, new StartTicketRequest(initialVersion)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Verify status, version, and assignment acceptedAt rolled back
        tx(() -> {
            Ticket ticket = em.find(Ticket.class, ticketId);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
            assertThat(ticket.getVersion()).isEqualTo(initialVersion);

            TicketAssignment assignment = em.createQuery(
                    "select a from TicketAssignment a where a.ticketId = :id and a.endedAt is null",
                    TicketAssignment.class).setParameter("id", ticketId).getSingleResult();
            assertThat(assignment.getAcceptedAt()).isNull();
        });
    }

    @Test
    void startConcurrencyPreventsDuplicateExecution() throws Exception {
        Long ticketId = assignedTicket.getId();
        Long version = assignedTicket.getVersion();

        int threads = 2;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threads);
        var successCount = new AtomicInteger(0);
        var conflictOrErrorCount = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var res = startWork(ticketId, f.technician, "{\"version\":" + version + "}").andReturn();
                    int code = res.getResponse().getStatus();
                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else if (code == 409 || code == 422) {
                        conflictOrErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictOrErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictOrErrorCount.get()).isEqualTo(1);
    }

    // ==========================================
    // 3. Work Log API Tests
    // ==========================================

    @Test
    void getWorkLogsReturnsEmptyArrayWhenNoLogsExist() throws Exception {
        getWorkLogs(inProgressTicket.getId(), f.technician)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getWorkLogsReturnsChronologicalOrderAndFullDetailsWithoutTicketVersion() throws Exception {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-10-15T09:00:00+07:00");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");

        tx(() -> {
            em.persist(new WorkLog(inProgressTicket, f.technician, WorkLogType.DIAGNOSIS,
                    "Diagnosis step", t1, t1.plusMinutes(20), 20, t1));
            em.persist(new WorkLog(inProgressTicket, f.technician, WorkLogType.REPAIR,
                    "Repair step", t2, t2.plusMinutes(30), 30, t2));
            em.flush();
        });

        var res = getWorkLogs(inProgressTicket.getId(), f.technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode array = json.readTree(res.getResponse().getContentAsByteArray());
        JsonNode first = array.get(0);
        assertThat(first.get("ticketId").asLong()).isEqualTo(inProgressTicket.getId());
        assertThat(first.get("logType").asText()).isEqualTo("DIAGNOSIS");
        assertThat(first.get("content").asText()).isEqualTo("Diagnosis step");
        assertThat(first.get("minutesSpent").asInt()).isEqualTo(20);
        assertThat(first.at("/technician/id").asLong()).isEqualTo(f.technician.getId());
        assertThat(first.at("/technician/fullName").asText()).isEqualTo(f.technician.getFullName());
        assertThat(first.has("ticketVersion")).isFalse();

        JsonNode second = array.get(1);
        assertThat(second.get("logType").asText()).isEqualTo("REPAIR");
        assertThat(second.has("ticketVersion")).isFalse();
    }

    @Test
    void getWorkLogsRejectsUnexpectedQueryParametersWith400() throws Exception {
        getWorkLogs(inProgressTicket.getId(), f.technician, "page", "0")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/unsupported-filter"));
    }

    @Test
    void getWorkLogsVisibilityPermissions() throws Exception {
        // Reporter can read work logs
        getWorkLogs(inProgressTicket.getId(), inProgressTicket.getReporter())
                .andExpect(status().isOk());

        // Manager can read work logs
        getWorkLogs(inProgressTicket.getId(), f.manager)
                .andExpect(status().isOk());

        // Admin can read work logs
        getWorkLogs(inProgressTicket.getId(), f.admin)
                .andExpect(status().isOk());

        // Missing or inaccessible ticket -> 404
        getWorkLogs(999999L, f.technician)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @MethodSource("com.nexora.ticket.testsupport.TechnicianWorkApiContract#validLogTypes")
    void addWorkLogAcceptsAllFiveLogTypes(WorkLogType logType) throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        String body = """
                {
                    "version": %d,
                    "logType": "%s",
                    "content": "Work completed for %s",
                    "startedAt": "2026-10-15T09:00:00+07:00",
                    "endedAt": "2026-10-15T09:30:00+07:00",
                    "minutesSpent": 30
                }
                """.formatted(version, logType.name(), logType.name());

        var res = addWorkLog(ticketId, f.technician, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logType").value(logType.name()))
                .andExpect(jsonPath("$.content").value("Work completed for " + logType.name()))
                .andExpect(jsonPath("$.ticketVersion").value(version + 1))
                .andExpect(jsonPath("$.technician.id").value(f.technician.getId()))
                .andReturn();

        JsonNode data = json.readTree(res.getResponse().getContentAsByteArray());
        assertThat(data.has("id")).isTrue();

        // Verify database persistence
        tx(() -> {
            Ticket ticket = em.find(Ticket.class, ticketId);
            assertThat(ticket.getVersion()).isEqualTo(version + 1);
            List<WorkLog> logs = workLogRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getLogType()).isEqualTo(logType);
        });
    }

    static Stream<WorkLogType> validLogTypes() {
        return Stream.of(WorkLogType.values());
    }

    @Test
    void addWorkLogSupportsNullableEndedAtAndMinutesSpentWithoutAutocalculation() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        String body = """
                {
                    "version": %d,
                    "logType": "PROGRESS",
                    "content": "Progress report without endedAt or minutes",
                    "startedAt": "2026-10-15T09:00:00Z"
                }
                """.formatted(version);

        addWorkLog(ticketId, f.technician, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endedAt").doesNotExist())
                .andExpect(jsonPath("$.minutesSpent").doesNotExist())
                .andExpect(jsonPath("$.ticketVersion").value(version + 1));

        tx(() -> {
            List<WorkLog> logs = workLogRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getEndedAt()).isNull();
            assertThat(logs.get(0).getMinutesSpent()).isNull();
        });
    }

    @ParameterizedTest
    @MethodSource("com.nexora.ticket.testsupport.TechnicianWorkApiContract#invalidWorkLogPayloads")
    void addWorkLogValidationRejections(String payload, String expectedFieldError) throws Exception {
        addWorkLog(inProgressTicket.getId(), f.technician, payload)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.fieldErrors." + expectedFieldError).exists());
    }

    static Stream<Arguments> invalidWorkLogPayloads() {
        return Stream.of(
                // Missing version
                Arguments.of("{\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\"}", "version"),
                // Negative version
                Arguments.of("{\"version\":-1,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\"}", "version"),
                // Invalid logType enum
                Arguments.of("{\"version\":0,\"logType\":\"INVALID_TYPE\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\"}", "logType"),
                // Blank content
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"   \",\"startedAt\":\"2026-10-15T09:00:00Z\"}", "content"),
                // Missing startedAt
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\"}", "startedAt"),
                // startedAt without offset
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00\"}", "startedAt"),
                // endedAt < startedAt
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T10:00:00Z\",\"endedAt\":\"2026-10-15T09:00:00Z\"}", "endedAt"),
                // minutesSpent = 0
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\",\"minutesSpent\":0}", "minutesSpent"),
                // minutesSpent negative
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\",\"minutesSpent\":-10}", "minutesSpent"),
                // minutesSpent overflow (greater than int)
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\",\"minutesSpent\":99999999999}", "minutesSpent"),
                // Unsupported extra field
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\",\"author\":\"technician\"}", "author"),
                Arguments.of("{\"version\":0,\"logType\":\"PROGRESS\",\"content\":\"valid\",\"startedAt\":\"2026-10-15T09:00:00Z\",\"technicianId\":21}", "technicianId")
        );
    }

    @Test
    void addWorkLogPermissionsAndStateRules() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();
        String validBody = "{\"version\":" + version + ",\"logType\":\"PROGRESS\",\"content\":\"Test\",\"startedAt\":\"2026-10-15T09:00:00Z\"}";

        // Reporter cannot add log -> 403
        addWorkLog(ticketId, f.requester, validBody).andExpect(status().isForbidden());

        // Manager cannot add log -> 403
        addWorkLog(ticketId, f.manager, validBody).andExpect(status().isForbidden());

        // Technician without assignment on ticket -> 422
        addWorkLog(ticketId, f.multi, validBody).andExpect(status().isUnprocessableEntity());

        // Stale version -> 409
        addWorkLog(ticketId, f.technician, "{\"version\":" + (version + 99) + ",\"logType\":\"PROGRESS\",\"content\":\"Test\",\"startedAt\":\"2026-10-15T09:00:00Z\"}")
                .andExpect(status().isConflict());

        // Ticket not in IN_PROGRESS (e.g. assignedTicket is in ASSIGNED status) -> 422
        addWorkLog(assignedTicket.getId(), f.technician, "{\"version\":" + assignedTicket.getVersion() + ",\"logType\":\"PROGRESS\",\"content\":\"Test\",\"startedAt\":\"2026-10-15T09:00:00Z\"}")
                .andExpect(status().isUnprocessableEntity());

        // Non-existent ticket -> 404
        addWorkLog(999999L, f.technician, validBody).andExpect(status().isNotFound());
    }

    @Test
    void addWorkLogDoesNotCreateStatusHistory() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        long historyCountBefore = tx(() -> em.createQuery(
                "select count(h) from TicketStatusHistory h where h.ticket.id = :id", Long.class)
                .setParameter("id", ticketId).getSingleResult());

        addWorkLog(ticketId, f.technician, "{\"version\":" + version + ",\"logType\":\"REPAIR\",\"content\":\"Repaired cable\",\"startedAt\":\"2026-10-15T09:00:00Z\"}")
                .andExpect(status().isCreated());

        long historyCountAfter = tx(() -> em.createQuery(
                "select count(h) from TicketStatusHistory h where h.ticket.id = :id", Long.class)
                .setParameter("id", ticketId).getSingleResult());

        assertThat(historyCountAfter).isEqualTo(historyCountBefore);
    }

    // ==========================================
    // 4. Resolve API Tests
    // ==========================================

    @Test
    void resolveRequiresPriorSavedWorkLog() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        String body = """
                {
                    "version": %d,
                    "resolutionOutcome": "FIXED",
                    "resolutionSummary": "Summary without prior log"
                }
                """.formatted(version);

        resolveTicket(ticketId, f.technician, body)
                .andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @MethodSource("com.nexora.ticket.testsupport.TechnicianWorkApiContract#invalidResolvePayloads")
    void resolveValidationRejections(String payload, String expectedFieldError) throws Exception {
        resolveTicket(inProgressTicket.getId(), f.technician, payload)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.fieldErrors." + expectedFieldError).exists());
    }

    static Stream<Arguments> invalidResolvePayloads() {
        return Stream.of(
                // Missing version
                Arguments.of("{\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Done\"}", "version"),
                // Negative version
                Arguments.of("{\"version\":-1,\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Done\"}", "version"),
                // DUPLICATE outcome is rejected at resolve endpoint
                Arguments.of("{\"version\":0,\"resolutionOutcome\":\"DUPLICATE\",\"resolutionSummary\":\"Duplicate ticket\"}", "resolutionOutcome"),
                // Invalid outcome enum
                Arguments.of("{\"version\":0,\"resolutionOutcome\":\"INVALID\",\"resolutionSummary\":\"Done\"}", "resolutionOutcome"),
                // Blank resolutionSummary
                Arguments.of("{\"version\":0,\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"   \"}", "resolutionSummary"),
                // resolutionSummary > 1000 characters
                Arguments.of("{\"version\":0,\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"" + "a".repeat(1001) + "\"}", "resolutionSummary"),
                // Extra unsupported field
                Arguments.of("{\"version\":0,\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Done\",\"extra\":\"not allowed\"}", "extra")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIXED", "TEMPORARY_FIX", "NO_FAULT_FOUND", "UNRESOLVED"})
    void resolveSuccessfulFlowWithAllFourOutcomes(String outcome) throws Exception {
        Long ticketId = inProgressTicket.getId();

        // 1. Add work log first
        Long version = inProgressTicket.getVersion();
        addWorkLog(ticketId, f.technician, """
                {
                    "version": %d,
                    "logType": "DIAGNOSIS",
                    "content": "Checked the device",
                    "startedAt": "2026-10-15T09:00:00Z"
                }
                """.formatted(version)).andExpect(status().isCreated());

        Long newVersion = version + 1;

        // Verify active assignment exists before resolve
        long initialActiveCount = tx(() -> {
            long count = assignmentRepository.countActiveAssignmentsByTechnicianId(f.technician.getId());
            assertThat(count).isGreaterThanOrEqualTo(1);
            return count;
        });

        // 2. Resolve ticket
        String summary = "Resolution summary for outcome " + outcome;
        String resolveBody = """
                {
                    "version": %d,
                    "resolutionOutcome": "%s",
                    "resolutionSummary": "%s"
                }
                """.formatted(newVersion, outcome, summary);

        resolveTicket(ticketId, f.technician, resolveBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.version").value(newVersion + 1))
                .andExpect(jsonPath("$.currentAssignment").doesNotExist());

        // 3. Verify database state
        tx(() -> {
            Ticket ticket = em.find(Ticket.class, ticketId);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(ticket.getResolutionOutcome()).isEqualTo(ResolutionOutcome.valueOf(outcome));
            assertThat(ticket.getResolutionSummary()).isEqualTo(summary);
            assertThat(ticket.getResolvedAt()).isNotNull();
            assertThat(ticket.getClosedAt()).isNull();

            // Assignment terminated
            List<TicketAssignment> assignments = assignmentRepository.findByTicketIdOrderByAssignedAtDescIdDesc(ticketId);
            assertThat(assignments).isNotEmpty();
            TicketAssignment assignment = assignments.stream()
                    .filter(a -> "RESOLVED".equals(a.getEndReason()))
                    .findFirst()
                    .orElseThrow();
            assertThat(assignment.getEndedAt()).isNotNull();
            assertThat(assignment.getEndedAt()).isEqualTo(ticket.getResolvedAt());
            assertThat(assignment.getEndReason()).isEqualTo("RESOLVED");
            assertThat(assignmentRepository.findByTicketIdAndEndedAtIsNull(ticketId)).isEmpty();

            // Capacity released
            long activeCount = assignmentRepository.countActiveAssignmentsByTechnicianId(f.technician.getId());
            assertThat(activeCount).isEqualTo(initialActiveCount - 1);

            // History written
            List<TicketStatusHistory> histories = em.createQuery(
                    "select h from TicketStatusHistory h where h.ticket.id = :id and h.transitionAction = :action",
                    TicketStatusHistory.class)
                    .setParameter("id", ticketId)
                    .setParameter("action", TicketAction.RESOLVE)
                    .getResultList();
            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).getFromStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(histories.get(0).getToStatus()).isEqualTo(TicketStatus.RESOLVED);
        });

        // 4. Verify Ticket detail endpoint returns all 4 resolution fields
        getTicketDetail(ticketId, f.technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionOutcome").value(outcome))
                .andExpect(jsonPath("$.resolutionSummary").value(summary))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").isEmpty());
    }

    @Test
    void resolvePermissionsAndStateChecks() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        // Add a work log
        addWorkLog(ticketId, f.technician, "{\"version\":" + version + ",\"logType\":\"REPAIR\",\"content\":\"Done\",\"startedAt\":\"2026-10-15T09:00:00Z\"}")
                .andExpect(status().isCreated());
        Long currentVersion = version + 1;

        String validResolve = "{\"version\":" + currentVersion + ",\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Fixed properly\"}";

        // Reporter cannot resolve -> 403
        resolveTicket(ticketId, f.requester, validResolve).andExpect(status().isForbidden());

        // Manager cannot resolve -> 403
        resolveTicket(ticketId, f.manager, validResolve).andExpect(status().isForbidden());

        // Unassigned technician cannot resolve -> 422
        resolveTicket(ticketId, f.multi, validResolve).andExpect(status().isUnprocessableEntity());

        // Ticket not in IN_PROGRESS status (e.g. assignedTicket is ASSIGNED) -> 422
        resolveTicket(assignedTicket.getId(), f.technician, "{\"version\":" + assignedTicket.getVersion() + ",\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Fixed\"}")
                .andExpect(status().isUnprocessableEntity());

        // Stale version -> 409
        resolveTicket(ticketId, f.technician, "{\"version\":" + (currentVersion + 10) + ",\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Fixed\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void ticketDetailReturnsNullForResolutionFieldsBeforeResolve() throws Exception {
        getTicketDetail(inProgressTicket.getId(), f.technician)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionOutcome").isEmpty())
                .andExpect(jsonPath("$.resolutionSummary").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.closedAt").isEmpty());
    }

    @Test
    void concurrentResolveReturnsOneSuccessAndOneConflict() throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();

        // Add log first
        addWorkLog(ticketId, f.technician, "{\"version\":" + version + ",\"logType\":\"PROGRESS\",\"content\":\"Logged\",\"startedAt\":\"2026-10-15T09:00:00Z\"}")
                .andExpect(status().isCreated());
        Long newVersion = version + 1;

        int threads = 2;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threads);
        var successCount = new AtomicInteger(0);
        var conflictCount = new AtomicInteger(0);
        var executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var res = resolveTicket(ticketId, f.technician, """
                            {
                                "version": %d,
                                "resolutionOutcome": "FIXED",
                                "resolutionSummary": "Concurrent resolve attempt"
                            }
                            """.formatted(newVersion)).andReturn();
                    int code = res.getResponse().getStatus();
                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else if (code == 409 || code == 422) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTER", "TECHNICIAN", "MANAGER", "ADMIN"})
    void closeTicket_reporterAndMixedRoles_preserveResolutionAndHistory(String extraRole) throws Exception {
        Long ticketId = inProgressTicket.getId();
        Long version = inProgressTicket.getVersion();
        addWorkLog(ticketId, f.technician, """
                {"version":%d,"logType":"RESOLUTION","content":"Fixed issue","startedAt":"2026-10-15T09:00:00Z"}
                """.formatted(version)).andExpect(status().isCreated());
        resolveTicket(ticketId, f.technician, """
                {"version":%d,"resolutionOutcome":"FIXED","resolutionSummary":"Completed successfully"}
                """.formatted(version + 1)).andExpect(status().isOk());
        // The fixture reporter already has REQUESTER + TECHNICIAN. Exercise the other role unions too.
        if (extraRole.equals("MANAGER") || extraRole.equals("ADMIN")) {
            tx(() -> em.createNativeQuery("insert into dbo.user_roles (user_id, role_id, assigned_at) "
                    + "select :userId, id, :time from dbo.roles where code = :role")
                    .setParameter("userId", f.multi.getId()).setParameter("time", TicketReadFixtures.TIME)
                    .setParameter("role", extraRole).executeUpdate());
        } else if (extraRole.equals("REQUESTER")) {
            tx(() -> em.createNativeQuery("delete from dbo.user_roles where user_id = :userId "
                    + "and role_id in (select id from dbo.roles where code = 'TECHNICIAN')")
                    .setParameter("userId", f.multi.getId()).executeUpdate());
        }
        try {
            JsonNode before = json.readTree(getTicketDetail(ticketId, f.multi).andReturn().getResponse().getContentAsByteArray());
            long resolvedVersion = before.path("version").asLong();
            closeTicket(ticketId, f.multi, """
                    {"version":%d,"reason":"  Đã kiểm tra, thiết bị hoạt động trở lại.  "}
                    """.formatted(resolvedVersion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ticketId))
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.version").value(resolvedVersion + 1))
                    .andExpect(jsonPath("$.currentAssignment").isEmpty());
            JsonNode after = json.readTree(getTicketDetail(ticketId, f.multi).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.closedAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsByteArray());
            for (String field : List.of("resolvedAt", "resolutionOutcome", "resolutionSummary")) {
                assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
            }
            JsonNode latest = after.path("statusHistory").get(after.path("statusHistory").size() - 1);
            assertThat(latest.path("transitionAction").asText()).isEqualTo("CLOSE");
            assertThat(latest.path("reason").asText()).isEqualTo("Đã kiểm tra, thiết bị hoạt động trở lại.");
            assertThat(latest.path("changedBy").path("id").asLong()).isEqualTo(f.multi.getId());
            assertThat(latest.path("changedAt")).isEqualTo(after.path("closedAt"));
            closeTicket(ticketId, f.multi, "{\"version\":" + resolvedVersion + "}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://nexora.app/problems/stale-write"));
            assertThat(historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(ticketId)).hasSize(2);
        } finally {
            // Added roles are outside TicketReadFixtures' owned rows.
            if (extraRole.equals("MANAGER") || extraRole.equals("ADMIN")) {
                tx(() -> em.createNativeQuery("delete from dbo.user_roles where user_id = :userId "
                        + "and role_id in (select id from dbo.roles where code = :role)")
                        .setParameter("userId", f.multi.getId()).setParameter("role", extraRole).executeUpdate());
            }
        }
    }

    private Long resolvedOwnTicket() {
        return tx(() -> {
            Ticket ticket = em.find(Ticket.class, f.own.getId());
            ticket.resolve(ResolutionOutcome.FIXED, "Saved resolution", TicketReadFixtures.TIME);
            em.flush();
            return ticket.getVersion();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"reason\":null", ",\"reason\":\"   \"", ",\"reason\":\"  checked  \""})
    void closeTicket_normalizesOptionalReason(String reasonField) throws Exception {
        Long version = resolvedOwnTicket();
        closeTicket(f.own.getId(), f.requester, "{\"version\":" + version + reasonField + "}")
                .andExpect(status().isOk());
        var entries = historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(f.own.getId());
        assertThat(entries.getLast().getReason()).isEqualTo(reasonField.contains("checked") ? "checked" : null);
    }

    @Test
    void closeTicket_accepts500CharactersAfterTrim() throws Exception {
        Long version = resolvedOwnTicket();
        closeTicket(f.own.getId(), f.requester, "{\"version\":" + version + ",\"reason\":\"  " + "a".repeat(500) + "  \"}")
                .andExpect(status().isOk());
        assertThat(historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(f.own.getId()).getLast().getReason())
                .hasSize(500);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "[]", "{", "{\"version\":null}", "{\"version\":-1}",
            "{\"version\":0.5}", "{\"version\":\"0\"}", "{\"version\":true}",
            "{\"version\":0,\"reason\":4}", "{\"version\":0,\"reason\":{}}",
            "{\"version\":0,\"status\":\"CLOSED\"}", "{\"version\":0,\"reporterId\":1}",
            "{\"version\":0,\"force\":true}", "{\"version\":0} {}"
    })
    void closeTicket_rejectsInvalidPayload(String body) throws Exception {
        closeTicket(f.own.getId(), f.requester, body).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void closeTicket_rejectsLongReasonAndInvalidPath() throws Exception {
        closeTicket(f.own.getId(), f.requester, "{\"version\":0,\"reason\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        for (String id : List.of("0", "-1", "abc", "1.5")) {
            mvc.perform(post("/api/v1/tickets/" + id + "/close").with(jwt().jwt(token(f.requester)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void closeTicket_visibilityAndPermissionPrecedeVersionAndState() throws Exception {
        for (AppUser actor : List.of(f.manager, f.admin, f.technician)) {
            // Technician can see the historical assignment; none of these users is the reporter.
            closeTicket(inProgressTicket.getId(), actor, "{\"version\":999,\"reason\":\"force\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        for (AppUser actor : List.of(f.other, f.noRole)) {
            closeTicket(f.own.getId(), actor, "{\"version\":999}").andExpect(status().isNotFound());
        }
        closeTicket(Long.MAX_VALUE, f.requester, "{\"version\":999}").andExpect(status().isNotFound());
        // Historical assignment alone grants no visibility once TECHNICIAN has been revoked.
        tx(() -> em.createNativeQuery("delete from dbo.user_roles where user_id = :userId")
                .setParameter("userId", f.technician.getId()).executeUpdate());
        closeTicket(inProgressTicket.getId(), f.technician, "{\"version\":999}").andExpect(status().isNotFound());
    }

    @Test
    void closeTicket_reporterWithoutRequesterIsForbiddenWhenVisible() throws Exception {
        tx(() -> em.createNativeQuery("delete from dbo.user_roles where user_id = :userId "
                + "and role_id in (select id from dbo.roles where code = 'REQUESTER')")
                .setParameter("userId", f.multi.getId()).executeUpdate());
        // Give this TECHNICIAN reporter visibility through an ended assignment.
        tx(() -> {
            var assignment = new TicketAssignment(inProgressTicket.getId(), f.multi.getId(), f.manager.getId(), TicketReadFixtures.TIME);
            assignment.end(TicketReadFixtures.TIME.plusHours(1), "Finished");
            em.persist(assignment);
        });
        closeTicket(inProgressTicket.getId(), f.multi, "{\"version\":999}").andExpect(status().isForbidden());
    }

    @Test
    void closeTicket_rejectsInvalidCurrentUser() throws Exception {
        closeTicket(f.own.getId(), null, "{\"version\":0}").andExpect(status().isUnauthorized());
        tx(() -> em.find(AppUser.class, f.requester.getId()).changeStatus(com.nexora.user.entity.UserStatus.DISABLED));
        closeTicket(f.own.getId(), f.requester, "{\"version\":0}").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tickets/{id}/close", f.own.getId())
                .with(jwt().jwt(Jwt.withTokenValue("test").header("alg", "HS256").subject("not-a-user").build()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tickets/{id}/close", f.own.getId())
                .with(jwt().jwt(Jwt.withTokenValue("test").header("alg", "HS256").subject(Long.MAX_VALUE + "").build()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "IN_PROGRESS", "CLOSED"})
    void closeTicket_requiresResolvedWithCurrentVersion(String state) throws Exception {
        Long version = tx(() -> {
            Ticket ticket = em.find(Ticket.class, f.own.getId());
            ticket.changeStatus(TicketStatus.valueOf(state));
            em.flush();
            return ticket.getVersion();
        });
        closeTicket(f.own.getId(), f.requester, "{\"version\":" + version + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void closeTicket_rejectsInconsistentAssignmentWithoutEndingIt() throws Exception {
        Long version = tx(() -> {
            Ticket ticket = em.find(Ticket.class, inProgressTicket.getId());
            ticket.resolve(ResolutionOutcome.FIXED, "Inconsistent fixture", TicketReadFixtures.TIME);
            em.flush();
            return ticket.getVersion();
        });
        closeTicket(inProgressTicket.getId(), f.multi, "{\"version\":" + version + "}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(assignmentRepository.existsByTicketIdAndEndedAtIsNull(inProgressTicket.getId())).isTrue();
        tx(() -> assertThat(em.find(Ticket.class, inProgressTicket.getId()).getClosedAt()).isNull());
    }

    @Test
    void closeTicket_historyFailureRollsBackAllChanges() {
        Long version = resolvedOwnTicket();
        var before = historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(f.own.getId());
        doThrow(new DataIntegrityViolationException("Simulated close history failure"))
                .when(historyRepository).save(any(TicketStatusHistory.class));
        assertThatThrownBy(() -> closeTicket(f.own.getId(), f.requester, "{\"version\":" + version + "}"))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);
        tx(() -> {
            Ticket ticket = em.find(Ticket.class, f.own.getId());
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(ticket.getClosedAt()).isNull();
            assertThat(ticket.getVersion()).isEqualTo(version);
            assertThat(ticket.getResolvedAt()).isEqualTo(TicketReadFixtures.TIME);
        });
        assertThat(historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(f.own.getId())).hasSize(before.size());
    }

    @Test
    void closeTicket_concurrentRequestsProduceExactlyOneSuccessAndOneStaleWrite() throws Exception {
        Long version = resolvedOwnTicket();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return closeTicket(f.own.getId(), f.requester, "{\"version\":" + version + "}")
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(200, 409);
            } finally {
                start.countDown();
            }
        }
        tx(() -> assertThat(em.find(Ticket.class, f.own.getId()).getVersion()).isEqualTo(version + 1));
        assertThat(historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(f.own.getId()).stream()
                .filter(h -> h.getTransitionAction() == TicketAction.CLOSE).count()).isEqualTo(1);
    }

    @Test
    void openApiIncludesAllWorkEndpoints() throws Exception {
        var doc = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        // My work endpoint
        assertThat(doc.path("paths").has("/api/v1/technicians/me/assigned-tickets")).isTrue();
        var myWork = doc.path("paths").path("/api/v1/technicians/me/assigned-tickets").path("get");
        assertThat(myWork.path("responses").has("200")).isTrue();
        assertThat(myWork.path("responses").has("400")).isTrue();
        assertThat(myWork.path("responses").has("401")).isTrue();
        assertThat(myWork.path("responses").has("403")).isTrue();

        // Start endpoint
        assertThat(doc.path("paths").has("/api/v1/tickets/{ticketId}/start")).isTrue();
        var start = doc.path("paths").path("/api/v1/tickets/{ticketId}/start").path("post");
        assertThat(start.path("responses").has("200")).isTrue();
        assertThat(start.path("responses").has("400")).isTrue();
        assertThat(start.path("responses").has("409")).isTrue();
        assertThat(start.path("responses").has("422")).isTrue();

        // Work logs GET endpoint
        assertThat(doc.path("paths").has("/api/v1/tickets/{ticketId}/work-logs")).isTrue();
        var workLogsGet = doc.path("paths").path("/api/v1/tickets/{ticketId}/work-logs").path("get");
        assertThat(workLogsGet.path("responses").has("200")).isTrue();
        assertThat(workLogsGet.path("responses").has("400")).isTrue();
        assertThat(workLogsGet.path("responses").has("401")).isTrue();
        assertThat(workLogsGet.path("responses").has("404")).isTrue();

        // Work logs POST endpoint
        var workLogsPost = doc.path("paths").path("/api/v1/tickets/{ticketId}/work-logs").path("post");
        assertThat(workLogsPost.path("responses").has("201")).isTrue();
        assertThat(workLogsPost.path("responses").has("400")).isTrue();
        assertThat(workLogsPost.path("responses").has("401")).isTrue();
        assertThat(workLogsPost.path("responses").has("403")).isTrue();
        assertThat(workLogsPost.path("responses").has("404")).isTrue();
        assertThat(workLogsPost.path("responses").has("409")).isTrue();
        assertThat(workLogsPost.path("responses").has("422")).isTrue();

        // Resolve endpoint
        assertThat(doc.path("paths").has("/api/v1/tickets/{ticketId}/resolve")).isTrue();
        var resolve = doc.path("paths").path("/api/v1/tickets/{ticketId}/resolve").path("post");
        assertThat(resolve.path("responses").has("200")).isTrue();
        assertThat(resolve.path("responses").has("400")).isTrue();
        assertThat(resolve.path("responses").has("401")).isTrue();
        assertThat(resolve.path("responses").has("403")).isTrue();
        assertThat(resolve.path("responses").has("404")).isTrue();
        assertThat(resolve.path("responses").has("409")).isTrue();
        assertThat(resolve.path("responses").has("422")).isTrue();

        // Close endpoint
        assertThat(doc.path("paths").has("/api/v1/tickets/{ticketId}/close")).isTrue();
        var close = doc.path("paths").path("/api/v1/tickets/{ticketId}/close").path("post");
        assertThat(close.path("responses").has("200")).isTrue();
        assertThat(close.path("responses").has("400")).isTrue();
        assertThat(close.path("responses").has("401")).isTrue();
        assertThat(close.path("responses").has("403")).isTrue();
        assertThat(close.path("responses").has("404")).isTrue();
        assertThat(close.path("responses").has("409")).isTrue();
        assertThat(close.path("responses").has("422")).isTrue();
    }
}
