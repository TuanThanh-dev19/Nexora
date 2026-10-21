package com.nexora.ticket.testsupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.request.ReviewTicketRequest;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
import com.nexora.ticket.entity.*;
import com.nexora.ticket.repository.*;
import com.nexora.ticket.service.TicketAssignmentService;
import com.nexora.user.entity.*;
import com.nexora.user.repository.TechnicianProfileRepository;
import com.nexora.user.service.TechnicianService;
import com.nexora.user.service.AdminUserService;
import com.nexora.user.dto.TechnicianUpdateRequest;
import com.nexora.user.dto.TechnicianServiceAreasUpdateRequest;
import com.nexora.user.dto.UserRolesUpdateRequest;
import com.nexora.user.dto.UserStatusUpdateRequest;
import com.nexora.location.entity.Location;
import jakarta.persistence.EntityManager;

/** Executes the same HTTP/transaction contract on H2 and on the migrated SQL Server schema. */
public abstract class TicketActionApiContract {
    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected ObjectMapper json;
    @Autowired protected TicketAssignmentService service;
    @Autowired protected TechnicianService technicians;
    @Autowired protected AdminUserService users;
    @Autowired protected TechnicianProfileRepository profiles;
    @MockitoSpyBean protected TicketStatusHistoryRepository history;
    @MockitoSpyBean protected TicketAssignmentRepository assignments;
    protected TicketReadFixtures f;

    @BeforeEach
    void setup() {
        tx(() -> {
            f = new TicketReadFixtures(em).create();
            em.createQuery("delete from TicketAssignment a where a.ticketId in :ids")
                    .setParameter("ids", f.ticketIds()).executeUpdate();
            for (AppUser user : List.of(f.technician, f.multi)) {
                TechnicianProfile profile = em.find(TechnicianProfile.class, user.getId());
                profile.changeMaxActiveTickets(1);
                em.persist(new TechnicianServiceArea(profile, f.location));
            }
        });
    }

    @AfterEach
    void cleanup() {
        if (f == null) return;
        tx(() -> {
            em.createQuery("delete from TicketStatusHistory h where h.ticket.id in :ids")
                    .setParameter("ids", f.ticketIds()).executeUpdate();
            em.createQuery("delete from TechnicianServiceArea a where a.technician.technicianId in :ids")
                    .setParameter("ids", List.of(f.technician.getId(), f.multi.getId())).executeUpdate();
            em.createQuery("delete from UserRole r where r.user.id = :id")
                    .setParameter("id", f.technician.getId()).executeUpdate();
            f.cleanup();
        });
    }

    protected void tx(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    protected Jwt token(AppUser user) {
        return Jwt.withTokenValue("test").header("alg", "HS256").subject(user.getId().toString()).build();
    }

    protected ResultActions action(String action, Long id, AppUser actor, String body) throws Exception {
        return mvc.perform(post("/api/v1/tickets/{id}/{action}", id, action)
                .with(jwt().jwt(token(actor))).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected TicketActionResultResponse review(Ticket ticket) {
        return service.review(token(f.manager), ticket.getId(), new ReviewTicketRequest(0L));
    }

    protected TicketActionResultResponse assign(Ticket ticket) {
        return service.assign(token(f.manager), ticket.getId(), new AssignTicketRequest(1L, f.technician.getId()));
    }

    protected JsonNode detail(Ticket ticket) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/tickets/{id}", ticket.getId()).with(jwt().jwt(token(f.manager))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void reviewAssignAndRefreshPreserveDetailAndReturnExactlyTheCommittedContract() throws Exception {
        JsonNode before = detail(f.own);
        assertThat(before.has("currentAssignment")).isTrue();
        assertThat(before.get("currentAssignment").isNull()).isTrue();
        JsonNode reviewed = json.readTree(action("review", f.own.getId(), f.manager, "{\"version\":0}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(reviewed.size()).isEqualTo(4);
        assertThat(reviewed.get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(reviewed.get("version").asLong()).isEqualTo(1);
        assertThat(reviewed.get("currentAssignment").isNull()).isTrue();
        JsonNode result = json.readTree(action("assign", f.own.getId(), f.admin,
                "{\"version\":1,\"technicianId\":" + f.technician.getId() + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(result.size()).isEqualTo(4);
        assertThat(result.get("version").asLong()).isEqualTo(2);
        var assignment = result.get("currentAssignment");
        assertThat(assignment.size()).isEqualTo(9);
        assertThat(assignment.get("id").asLong()).isPositive();
        assertThat(assignment.get("ticketId").asLong()).isEqualTo(f.own.getId());
        assertThat(assignment.get("technician").size()).isEqualTo(2);
        assertThat(assignment.at("/technician/technicianId").asLong()).isEqualTo(f.technician.getId());
        assertThat(assignment.at("/assignedBy/id").asLong()).isEqualTo(f.admin.getId());
        OffsetDateTime.parse(assignment.get("assignedAt").asText());
        for (String field : List.of("acceptedAt", "endedAt", "endReason", "overrideReason"))
            assertThat(assignment.get(field).isNull()).as(field).isTrue();
        JsonNode after = detail(f.own);
        assertThat(after.get("currentAssignment")).isEqualTo(assignment);
        assertThat(after.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(after.get("version").asLong()).isEqualTo(2);
        assertThat(after.get("updatedAt")).isNotEqualTo(before.get("updatedAt"));
        for (String field : List.of("description", "formVersion", "dynamicValues", "attachments", "reporter", "category", "location"))
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
        var entries = after.get("statusHistory");
        assertThat(entries.size()).isEqualTo(before.get("statusHistory").size() + 2);
        assertThat(entries.get(entries.size() - 2).get("transitionAction").asText()).isEqualTo("BEGIN_REVIEW");
        assertThat(entries.get(entries.size() - 1).get("transitionAction").asText()).isEqualTo("ASSIGN");
        assertThat(result.toString()).doesNotContain("passwordHash", "email", "storageKey", "roleAssignments");
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("review", "{}", "version"),
                Arguments.of("review", "{\"version\":null}", "version"),
                Arguments.of("review", "{\"version\":-1}", "version"),
                Arguments.of("review", "{\"version\":\"0\"}", "version"),
                Arguments.of("review", "{\"version\":0.5}", "version"),
                Arguments.of("review", "{\"version\":9223372036854775808}", "version"),
                Arguments.of("review", "{\"version\":0,\"technicianId\":1}", "technicianId"),
                Arguments.of("review", "{\"version\":0,\"status\":\"ASSIGNED\"}", "status"),
                Arguments.of("assign", "{\"technicianId\":1}", "version"),
                Arguments.of("assign", "{\"version\":0}", "technicianId"),
                Arguments.of("assign", "{\"version\":0,\"technicianId\":0}", "technicianId"),
                Arguments.of("assign", "{\"version\":0,\"technicianId\":\"1\"}", "technicianId"),
                Arguments.of("assign", "{\"version\":0,\"technicianId\":1.1}", "technicianId"),
                Arguments.of("assign", "{\"version\":0,\"technicianId\":true}", "technicianId"),
                Arguments.of("assign", "{\"version\":0,\"technicianId\":1,\"overrideReason\":\"\"}", "overrideReason"));
    }

    @ParameterizedTest @MethodSource("invalidBodies")
    void strictRequestsReturnFieldErrors(String action, String body, String field) throws Exception {
        action(action, f.own.getId(), f.manager, body).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
        assertThat(detail(f.own).get("version").asLong()).isZero();
    }

    @Test
    void authorizationUsesCurrentDatabaseRolesAndAllowsMixedManager() throws Exception {
        for (String name : List.of("review", "assign")) {
            String body = name.equals("review") ? "{\"version\":0}" : "{\"version\":0,\"technicianId\":1}";
            mvc.perform(post("/api/v1/tickets/{id}/{action}", f.own.getId(), name)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            for (AppUser user : List.of(f.requester, f.technician, f.multi))
                action(name, f.own.getId(), user, body).andExpect(status().isForbidden());
        }
        // A JWT claiming ADMIN cannot grant a Requester manager access.
        mvc.perform(post("/api/v1/tickets/{id}/review", f.own.getId()).with(jwt().jwt(j ->
                j.subject(f.requester.getId().toString()).claim("roles", List.of("ADMIN"))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        tx(() -> em.find(AppUser.class, f.admin.getId()).changeStatus(UserStatus.DISABLED));
        action("review", f.own.getId(), f.admin, "{\"version\":0}").andExpect(status().isUnauthorized());
        tx(() -> {
            Role role = em.createQuery("select r from Role r where r.code = :code", Role.class)
                    .setParameter("code", RoleCode.MANAGER).getSingleResult();
            em.persist(new UserRole(em.find(AppUser.class, f.multi.getId()), role, null));
        });
        try {
            action("review", f.own.getId(), f.multi, "{\"version\":0}").andExpect(status().isOk());
        } finally {
            tx(() -> em.createQuery("delete from UserRole r where r.user.id = :id and r.role.code = :code")
                    .setParameter("id", f.multi.getId()).setParameter("code", RoleCode.MANAGER).executeUpdate());
        }
    }

    @Test
    void staleVersionPrecedesStateAndMissingUserDiffersFromMissingProfile() throws Exception {
        action("review", Long.MAX_VALUE, f.manager, "{\"version\":0}").andExpect(status().isNotFound());
        review(f.own);
        action("review", f.own.getId(), f.manager, "{\"version\":0}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/stale-write"));
        action("review", f.own.getId(), f.manager, "{\"version\":1}").andExpect(status().isUnprocessableEntity());
        action("assign", f.own.getId(), f.manager, "{\"version\":1,\"technicianId\":9223372036854775807}")
                .andExpect(status().isNotFound());
        action("assign", f.own.getId(), f.manager, "{\"version\":1,\"technicianId\":" + f.requester.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(detail(f.own).get("version").asLong()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"availability", "coverage", "role", "capacity", "skill"})
    void changedEligibilityIsCheckedAtWriteTime(String change) throws Exception {
        review(f.own);
        tx(() -> {
            switch (change) {
                case "availability" -> em.find(TechnicianProfile.class, f.technician.getId()).changeAvailability(false);
                case "coverage" -> em.createQuery("delete from TechnicianServiceArea a where a.technician.technicianId = :id")
                        .setParameter("id", f.technician.getId()).executeUpdate();
                case "role" -> em.createQuery("delete from UserRole r where r.user.id = :id")
                        .setParameter("id", f.technician.getId()).executeUpdate();
                case "capacity" -> em.persist(new TicketAssignment(f.hidden.getId(), f.technician.getId(), f.manager.getId(), OffsetDateTime.now()));
                case "skill" -> { } // The required skill is provisioned and cleaned up below.
                default -> throw new AssertionError(change);
            }
        });
        if (change.equals("skill")) {
            Skill required = new Skill("ACTION-" + f.own.getId(), "Required skill");
            tx(() -> { em.persist(required); em.flush(); em.createNativeQuery("update dbo.tickets set required_skill_id = :skill where id = :id")
                    .setParameter("skill", required.getId()).setParameter("id", f.own.getId()).executeUpdate(); });
            try { assertThatThrownBy(() -> assign(f.own)).isInstanceOf(BusinessRuleException.class); }
            finally { tx(() -> { em.createNativeQuery("update dbo.tickets set required_skill_id = null where id = :id")
                    .setParameter("id", f.own.getId()).executeUpdate(); em.remove(em.find(Skill.class, required.getId())); }); }
        } else assertThatThrownBy(() -> assign(f.own)).isInstanceOf(BusinessRuleException.class);
        assertThat(detail(f.own).get("version").asLong()).isEqualTo(1);
    }

    @Test
    void endedAssignmentsAreNotCurrentAndLifecycleUpdatesPersist() throws Exception {
        review(f.own);
        var created = assign(f.own).currentAssignment();
        tx(() -> {
            TicketAssignment assignment = em.find(TicketAssignment.class, created.id());
            assignment.accept(created.assignedAt().plusSeconds(1));
            em.find(Ticket.class, f.own.getId()).changeStatus(TicketStatus.IN_PROGRESS);
        });
        assertThat(detail(f.own).at("/currentAssignment/acceptedAt").isTextual()).isTrue();
        tx(() -> {
            em.find(TicketAssignment.class, created.id()).end(created.assignedAt().plusSeconds(2), "Completed");
            em.find(Ticket.class, f.own.getId()).changeStatus(TicketStatus.RESOLVED);
        });
        assertThat(detail(f.own).get("currentAssignment").isNull()).isTrue();
        tx(() -> assertThat(em.find(TicketAssignment.class, created.id()).getEndReason()).isEqualTo("Completed"));
        tx(() -> em.find(Ticket.class, f.hidden.getId()).changeStatus(TicketStatus.REOPENED));
        var reopened = service.assign(token(f.manager), f.hidden.getId(), new AssignTicketRequest(1L, f.technician.getId()));
        assertThat(reopened.status()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void existingAssignmentIsRejectedWithoutChangingTicket() {
        review(f.own);
        tx(() -> em.persist(new TicketAssignment(f.own.getId(), f.multi.getId(), f.manager.getId(), OffsetDateTime.now())));
        assertThatThrownBy(() -> assign(f.own)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("active assignment");
    }

    @ParameterizedTest @ValueSource(strings = {"review-history", "assign-history", "assignment"})
    void persistenceFailureRollsBackEveryWrite(String failure) throws Exception {
        if (!failure.equals("review-history")) review(f.own);
        JsonNode before = detail(f.own);
        if (failure.equals("assignment")) doThrow(new DataIntegrityViolationException("Simulated assignment failure"))
                .when(assignments).save(any(TicketAssignment.class));
        else doThrow(new DataIntegrityViolationException("Simulated history failure"))
                .when(history).save(any(TicketStatusHistory.class));
        assertThatThrownBy(() -> { if (failure.equals("review-history")) review(f.own); else assign(f.own); })
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(detail(f.own)).isEqualTo(before);
    }

    @Test
    void concurrentAssignmentOfOneTicketReturnsOneSuccessAndOneStaleWrite() throws Exception {
        review(f.own);
        List<Object> results = race(() -> assign(f.own), () -> assign(f.own));
        assertThat(results.stream().filter(TicketActionResultResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(StaleWriteException.class::isInstance).count()).isEqualTo(1);
        assertThat(detail(f.own).get("version").asLong()).isEqualTo(2);
        assertThat(assignments.countActiveAssignmentsByTechnicianId(f.technician.getId())).isEqualTo(1);
    }

    @Test
    void differentTicketsCannotConsumeTheSameLastCapacitySlot() throws Exception {
        review(f.own); review(f.hidden);
        List<Object> results = race(() -> assign(f.own), () -> assign(f.hidden));
        assertThat(results.stream().filter(TicketActionResultResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(BusinessRuleException.class::isInstance).count()).isEqualTo(1);
        assertThat(assignments.countActiveAssignmentsByTechnicianId(f.technician.getId())).isEqualTo(1);
        assertThat(detail(f.own).get("version").asLong() + detail(f.hidden).get("version").asLong()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"availability", "coverage", "role", "status", "location"})
    void assignmentWaitsForConfigurationWriterAndReevaluatesCommittedState(String change) throws Exception {
        review(f.own);
        CountDownLatch changed = new CountDownLatch(1), release = new CountDownLatch(1), assigning = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> tx(() -> {
                switch (change) {
                    case "availability" -> technicians.updateProfile(f.technician.getId(), new TechnicianUpdateRequest(false, 1, null));
                    case "coverage" -> technicians.updateServiceAreas(f.technician.getId(), new TechnicianServiceAreasUpdateRequest(List.of(), null));
                    case "role" -> users.updateUserRoles(f.technician.getId(), new UserRolesUpdateRequest(Set.of(RoleCode.REQUESTER)), f.manager.getId());
                    case "status" -> users.updateUserStatus(f.technician.getId(), new UserStatusUpdateRequest(UserStatus.DISABLED, null));
                    case "location" -> em.find(Location.class, f.location.getId()).changeActive(false);
                    default -> throw new AssertionError(change);
                }
                em.flush();
                changed.countDown(); await(release);
            }));
            try {
                assertThat(changed.await(10, TimeUnit.SECONDS)).isTrue();
                var mutation = executor.submit(() -> { assigning.countDown(); return attempt(() -> assign(f.own)); });
                assertThat(assigning.await(10, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                writer.get(20, TimeUnit.SECONDS);
                assertThat(mutation.get(20, TimeUnit.SECONDS)).isInstanceOf(BusinessRuleException.class);
            } finally { release.countDown(); }
        }
        assertThat(detail(f.own).get("version").asLong()).isEqualTo(1);
    }

    @Test
    void openApiPublishesStrictActionsAndSharedAssignment() throws Exception {
        JsonNode document = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        for (String action : List.of("review", "assign")) {
            var endpoint = document.path("paths").path("/api/v1/tickets/{ticketId}/" + action).path("post");
            for (String code : List.of("200", "400", "401", "403", "404", "409", "422"))
                assertThat(endpoint.path("responses").has(code)).as(action + ":" + code).isTrue();
            assertThat(endpoint.path("requestBody").path("content").has("application/json")).isTrue();
        }
        assertThat(document.at("/components/schemas/TicketDetailResponse/properties").has("currentAssignment")).isTrue();
        assertThat(document.at("/components/schemas/AssignTicketRequest/additionalProperties").asBoolean(true)).isFalse();
    }

    protected List<Object> race(Supplier<?> first, Supplier<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { ready.countDown(); await(start); return attempt(first); });
            var b = executor.submit(() -> { ready.countDown(); await(start); return attempt(second); });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
                return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
            } finally { start.countDown(); }
        }
    }

    private static Object attempt(Supplier<?> action) {
        try { return action.get(); } catch (RuntimeException error) { return error; }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transaction"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
