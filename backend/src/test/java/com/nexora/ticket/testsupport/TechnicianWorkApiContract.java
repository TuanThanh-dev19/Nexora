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

public abstract class TechnicianWorkApiContract {

    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected ObjectMapper json;
    @Autowired protected TicketAssignmentService assignmentService;
    @Autowired protected TicketRepository ticketRepository;
    @Autowired protected TicketAssignmentRepository assignmentRepository;
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
                    inProgressTicket.getId(), f.technician.getId(), f.manager.getId(), TicketReadFixtures.TIME);
            em.persist(activeForHistorical);

            em.flush();
        });
    }

    @AfterEach
    void cleanup() {
        if (f == null) return;
        tx(() -> {
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

    @Test
    void openApiIncludesBothEndpoints() throws Exception {
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
    }
}
