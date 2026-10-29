package com.nexora.ticket.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.common.exception.ProblemTypes;
import com.nexora.user.entity.*;
import jakarta.persistence.EntityManager;

/** The same HTTP/SQL contract runs on fast H2 and real SQL Server. No enclosing test transaction. */
public abstract class TicketReadApiContract {
    private JsonNode search(AppUser viewer, String keyword) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/tickets").param("keyword",keyword)
                .with(jwt().jwt(j->j.subject(viewer.getId().toString())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test void keywordSearchRespectsEveryRoleUnionAndEmptyKeyword() throws Exception {
        for(var viewer:List.of(f.requester,f.other,f.technician,f.manager,f.admin,f.multi)) {
            assertThat(ids(search(viewer,"  "))).containsExactlyElementsOf(ids(list(viewer)));
            assertThat(ids(search(viewer,"rD-"))).containsAll(ids(list(viewer)));
        }
        assertThat(ids(search(f.requester,f.hidden.getTicketNumber()))).isEmpty();
        assertThat(ids(search(f.technician,f.own.getTicketNumber()))).isEmpty();
        assertThat(ids(search(f.multi,f.historical.getTicketNumber()))).containsExactly(f.historical.getId());
        assertThat(ids(search(f.multi,f.current.getTicketNumber()))).containsExactly(f.current.getId());
        assertThat(ids(search(f.technician,f.historical.getTicketNumber()))).containsExactly(f.historical.getId());
        assertThat(ids(search(f.manager,f.hidden.getTicketNumber()))).containsExactly(f.hidden.getId());
        assertThat(search(f.requester,"no-ticket-exists").path("totalElements").asLong()).isZero();
        for(var viewer:List.of(f.requester,f.manager))
            mvc.perform(get("/api/v1/tickets").param("keyword","x".repeat(201)).with(jwt().jwt(j->j.subject(viewer.getId().toString()))))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/tickets").param("keyword","", "").with(jwt().jwt(j->j.subject(f.manager.getId().toString())))).andExpect(status().isBadRequest());
    }

    @Test void literalWildcardUnicodeAndInjectionLikeInputNeverExpandMatches() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            em.createQuery("update Ticket t set t.title=:title where t.id=:id").setParameter("title","Máy CHIẾU 100%_[]!\\ đang hỏng").setParameter("id",f.own.getId()).executeUpdate();
            em.createQuery("update Ticket t set t.title=:title where t.id=:id").setParameter("title","Máy chiếu 100XYZ đang hỏng").setParameter("id",f.current.getId()).executeUpdate();
        });
        for(String keyword:List.of("%","_","[","]","!","\\","%_[]!\\","  máy chiếu 100%  "))
            assertThat(ids(search(f.manager,keyword))).containsExactly(f.own.getId());
        assertThat(ids(search(f.requester,"MÁY chiếu"))).containsExactly(f.own.getId());
        assertThat(ids(search(f.manager,"Máy CHIẾU 100%_[]!\\ đang hỏng"))).containsExactly(f.own.getId());
        assertThat(ids(search(f.manager,"' OR 1=1 --"))).isEmpty();
    }

    @Test void keywordCombinesWithAllFiltersBeforeStablePaginationAndCounts() throws Exception {
        var request=get("/api/v1/tickets").param("keyword","sỰ cỐ").param("status","SUBMITTED").param("priority","HIGH")
                .param("locationId",f.location.getId().toString()).param("sort","ticketNumber,asc").param("size","1").param("page","1")
                .with(jwt().jwt(j->j.subject(f.manager.getId().toString())));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(f.current.getId()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/tickets").param("keyword",f.own.getTicketNumber()).param("priority","LOW")
                .with(jwt().jwt(j->j.subject(f.manager.getId().toString())))).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(search(f.multi,"sự cố").path("totalElements").asLong()).isEqualTo(2);
    }
    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected ObjectMapper json;
    protected TicketReadFixtures f;

    @BeforeEach
    void setupFixture() {
        f = new TransactionTemplate(transactions).execute(status -> new TicketReadFixtures(em).create());
    }
    @AfterEach
    void cleanupFixture() {
        if (f != null) new TransactionTemplate(transactions).executeWithoutResult(status -> f.cleanup());
    }
    protected JsonNode list(AppUser viewer) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(viewer.getId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    protected List<Long> ids(JsonNode response) {
        var ids = new java.util.ArrayList<Long>();
        response.path("content").forEach(row -> ids.add(row.path("id").asLong()));
        return ids;
    }

    @Test
    void openApiPublishesListDetailAndAllSupportedQueryParameters() throws Exception {
        var document = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        var list = document.path("paths").path("/api/v1/tickets").path("get");
        var names = new java.util.ArrayList<String>();
        list.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("page", "size", "sort", "status", "priority", "locationId", "keyword");
        assertThat(list.path("description").asText()).contains("createdAt", "ticketNumber", "400");
        assertThat(list.path("responses").path("200").path("content").path("application/json")
                .path("schema").path("$ref").asText()).contains("PageResponse");
        assertThat(document.path("paths").path("/api/v1/tickets/{ticketId}").path("get")
                .path("responses").has("404")).isTrue();
        assertThat(document.path("components").path("schemas").path("TicketDetailResponse")
                .path("properties").has("dynamicValues")).isTrue();
        assertThat(document.path("components").path("schemas").has("TicketSummaryResponse")).isTrue();
    }

    @Test
    void visibilityUsesDatabaseRolesForAllFourRolesAndTheirUnion() throws Exception {
        assertThat(ids(list(f.requester))).containsExactly(f.own.getId());
        assertThat(ids(list(f.technician))).containsExactly(f.current.getId(), f.historical.getId());
        assertThat(ids(list(f.multi))).containsExactly(f.current.getId(), f.historical.getId());
        assertThat(ids(list(f.manager))).containsAll(f.ticketIds());
        assertThat(ids(list(f.admin))).containsAll(f.ticketIds());
        assertThat(list(f.technician).path("totalElements").asInt()).isEqualTo(2);
        // A forged/stale ADMIN claim does not expand the current REQUESTER's scope.
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(f.requester.getId().toString())
                        .claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (var viewer : List.of(f.manager, f.admin)) {
            for (Long id : f.ticketIds()) mvc.perform(get("/api/v1/tickets/{id}", id)
                    .with(jwt().jwt(j -> j.subject(viewer.getId().toString())))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/tickets/{id}", f.historical.getId())
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))).andExpect(status().isOk());
    }

    @Test
    void pagingCountFiltersAndStableSortAreAppliedInTheVisibleQuery() throws Exception {
        mvc.perform(get("/api/v1/tickets").param("page", "1").param("size", "1")
                        .with(jwt().jwt(j -> j.subject(f.technician.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(f.historical.getId()))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/tickets").param("status", "SUBMITTED").param("priority", "HIGH")
                        .param("locationId", f.location.getId().toString()).param("sort", "ticketNumber,desc")
                        .with(jwt().jwt(j -> j.subject(f.technician.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(f.current.getId()));
        for (String sort : List.of("createdAt,asc", "updatedAt,desc", "ticketNumber,asc")) {
            mvc.perform(get("/api/v1/tickets").param("sort", sort)
                    .with(jwt().jwt(j -> j.subject(f.manager.getId().toString()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(4));
        }
        mvc.perform(get("/api/v1/tickets").param("sort", "ticketNumber,desc")
                        .with(jwt().jwt(j -> j.subject(f.manager.getId().toString()))))
                .andExpect(jsonPath("$.content[0].id").value(f.hidden.getId()));
        mvc.perform(get("/api/v1/tickets").param("page", "99")
                        .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/tickets").param("locationId", Long.toString(Long.MAX_VALUE))
                        .with(jwt().jwt(j -> j.subject(f.manager.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void missingAndInvisibleDetailsHaveIdenticalSafeProblems() throws Exception {
        var denied = mvc.perform(get("/api/v1/tickets/{id}", f.hidden.getId())
                        .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsByteArray();
        var missing = mvc.perform(get("/api/v1/tickets/{id}", Long.MAX_VALUE)
                        .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsByteArray();
        for (String property : List.of("type", "title", "detail", "status")) {
            assertThat(json.readTree(denied).get(property)).isEqualTo(json.readTree(missing).get(property));
        }
        mvc.perform(get("/api/v1/tickets/{id}", f.hidden.getId())
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))).andExpect(status().isNotFound());
    }

    @Test
    void detailReadsArchivedFormAllFieldTypesHistoryAndOnlyPublicMetadataWithoutWriting() throws Exception {
        var response = mvc.perform(get("/api/v1/tickets/{id}", f.own.getId())
                        .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formVersion.id").value(f.form.getId()))
                .andExpect(jsonPath("$.formVersion.formStatus").value("ARCHIVED"))
                .andExpect(jsonPath("$.formVersion.fields.length()").value(9))
                .andExpect(jsonPath("$.formVersion.fields[3].options[0].label").value("Lựa chọn cũ"))
                .andExpect(jsonPath("$.dynamicValues.length()").value(8))
                .andExpect(jsonPath("$.dynamicValues[2].valueJson").value("9007199254740993"))
                .andExpect(jsonPath("$.dynamicValues[7].valueJson").value("false"))
                .andExpect(jsonPath("$.statusHistory[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$.statusHistory[1].transitionAction").value("REASSIGN"))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].fieldDefinitionId").isNumber())
                .andExpect(jsonPath("$.attachments[1].attachmentType").value("WORK_RESULT"))
                .andExpect(jsonPath("$.asset.assetCode").isString()).andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response).doesNotContain("storageKey", "passwordHash", "private/", "nội bộ.txt");
        var state = new TransactionTemplate(transactions).execute(status -> {
            var persisted = em.find(com.nexora.ticket.entity.Ticket.class, f.own.getId());
            return List.of(persisted.getVersion(), persisted.getUpdatedAt().toInstant());
        });
        assertThat(state.get(0)).isEqualTo(0L);
        assertThat(state.get(1)).isEqualTo(TicketReadFixtures.TIME.toInstant());
        mvc.perform(get("/api/v1/tickets/{id}", f.current.getId())
                        .with(jwt().jwt(j -> j.subject(f.manager.getId().toString()))))
                .andExpect(jsonPath("$.asset").isEmpty()).andExpect(jsonPath("$.slaDueAt").isEmpty())
                .andExpect(jsonPath("$.dynamicValues").isEmpty()).andExpect(jsonPath("$.statusHistory").isEmpty())
                .andExpect(jsonPath("$.attachments").isEmpty());
    }

    @Test
    void revokedAdminRoleIsNotGrantedByAnOldToken() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> em.createQuery(
                "delete from UserRole ur where ur.user.id = :id").setParameter("id", f.admin.getId()).executeUpdate());
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(f.admin.getId().toString())
                .claim("roles", List.of("ADMIN"))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tickets/{id}", f.own.getId()).with(jwt().jwt(j -> j.subject(f.admin.getId().toString())
                .claim("roles", List.of("ADMIN"))))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "DISABLED"})
    void inactiveUsersAreRejectedEvenWithValidJwt(String state) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                em.find(AppUser.class, f.requester.getId()).changeStatus(UserStatus.valueOf(state)));
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingUsersNoRolesAndMalformedSubjectsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(Long.toString(Long.MAX_VALUE)))))
                .andExpect(status().isUnauthorized());
        for (String subject : List.of("invalid", "0", "-1", "999999999999999999999")) {
            mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(subject))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type").value(ProblemTypes.UNAUTHENTICATED.toString()));
        }
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject(f.noRole.getId().toString()).claim("roles", List.of("ADMIN")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailExposesOnlyActiveAssignmentWhileHistoricalAssignmentsStillGrantVisibility() throws Exception {
        var current = json.readTree(mvc.perform(get("/api/v1/tickets/{id}", f.current.getId())
                        .with(jwt().jwt(j -> j.subject(f.technician.getId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(current.at("/currentAssignment/technician/technicianId").asLong()).isEqualTo(f.technician.getId());
        assertThat(current.at("/currentAssignment/assignedBy/fullName").asText()).isEqualTo(f.manager.getFullName());
        assertThat(current.at("/currentAssignment/endedAt").isNull()).isTrue();
        var historical = json.readTree(mvc.perform(get("/api/v1/tickets/{id}", f.historical.getId())
                        .with(jwt().jwt(j -> j.subject(f.technician.getId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(historical.has("currentAssignment")).isTrue();
        assertThat(historical.get("currentAssignment").isNull()).isTrue();
    }
}
