package com.nexora.ticket.testsupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.nexora.category.entity.*;
import com.nexora.common.storage.*;
import com.nexora.ticket.entity.*;
import com.nexora.user.entity.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/**
 * Same HTTP/transaction/storage contract on H2 and SQL Server, without an enclosing test
 * transaction.
 */
public abstract class TicketCreateApiContract {
    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected ObjectMapper json;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected DataSource source;
    @Autowired protected com.nexora.auth.security.JwtTokenBuilder tokens;

    @org.springframework.beans.factory.annotation.Value("${local.server.port:0}")
    protected int port;

    @MockitoSpyBean protected FileStorageService storage;
    protected TicketCreateFixtures f;
    protected static final Path ROOT;

    static {
        try {
            ROOT = Files.createTempDirectory("nexora-create-tests-");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.root", ROOT::toString);
    }

    @BeforeEach
    void setup() throws Exception {
        try (var connection = source.getConnection()) {
            if (connection.getMetaData().getDatabaseProductName().equals("H2"))
                try (var statement = connection.createStatement()) {
                    statement.execute(
                            "create sequence if not exists dbo.ticket_number_sequence as bigint"
                                    + " start with 1");
                }
        }
        f = tx(() -> new TicketCreateFixtures(em).create());
    }

    @AfterEach
    void cleanup() {
        if (f == null) return;
        reset(storage);
        List<String> keys =
                tx(
                        () ->
                                em.createQuery(
                                                "select a.storageKey from TicketAttachment a where"
                                                        + " a.ticket.category.id=:id",
                                                String.class)
                                        .setParameter("id", f.category.getId())
                                        .getResultList());
        for (String key : keys) storage.delete(key);
        tx(
                () -> {
                    f.cleanup();
                    return null;
                });
    }

    protected <T> T tx(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(s -> action.get());
    }

    protected ObjectNode payload() {
        var root = json.createObjectNode();
        root.put("categoryId", f.category.getId())
                .put("formVersionId", f.form.getId())
                .put("locationId", f.location.getId());
        root.put("title", "  Máy chiếu không lên nguồn  ")
                .put("description", "  Mô tả dài\nchi tiết  ");
        root.putArray("dynamicValues");
        root.putArray("attachments");
        return root;
    }

    protected MockPart image(String name, String mime, byte[] bytes) {
        var p = new MockPart("files", name, bytes);
        p.getHeaders().setContentType(MediaType.parseMediaType(mime));
        return p;
    }

    protected MockPart image() {
        return image(
                "evidence.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0});
    }

    protected MockPart ticket(String content, String mime) {
        var p = new MockPart("ticket", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        p.getHeaders().setContentType(MediaType.parseMediaType(mime));
        return p;
    }

    protected MockMultipartHttpServletRequestBuilder request(
            ObjectNode payload, MockPart... files) {
        var request =
                multipart("/api/v1/tickets").part(ticket(payload.toString(), "application/json"));
        for (var file : files) request.part(file);
        return request;
    }

    protected ResultActions post(ObjectNode payload, MockPart... files) throws Exception {
        return mvc.perform(
                request(payload, files)
                        .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))));
    }

    protected JsonNode created(ObjectNode payload, MockPart... files) throws Exception {
        var response =
                post(payload, files)
                        .andExpect(status().isCreated())
                        .andExpect(
                                header().string(
                                                "Location",
                                                org.hamcrest.Matchers.matchesPattern(
                                                        "/api/v1/tickets/\\d+")))
                        .andExpect(jsonPath("$.status").value("SUBMITTED"))
                        .andExpect(jsonPath("$.version").value(0))
                        .andReturn()
                        .getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    protected long count() {
        return tx(
                () ->
                        em.createQuery(
                                        "select count(t) from Ticket t where t.category.id=:id",
                                        Long.class)
                                .setParameter("id", f.category.getId())
                                .getSingleResult());
    }

    protected Set<Path> disk() throws Exception {
        try (var files = Files.list(ROOT)) {
            return new HashSet<>(files.toList());
        }
    }

    @Test
    void createsAndReadsAllValueTypesAndEvidenceWithServerSnapshots() throws Exception {
        var p = payload().put("assetId", f.asset.getId());
        ObjectNode values =
                json.createObjectNode()
                        .put("text", "Máy chiếu")
                        .put("textarea", "Dòng 1\nDòng 2")
                        .put("number", 0)
                        .put("select", "a")
                        .put("boolean", false)
                        .put("date", "2026-10-18")
                        .put("datetime", "2026-10-18T09:30:00+07:00");
        values.putArray("multi_select").add("a");
        values.fields()
                .forEachRemaining(
                        e ->
                                ((ArrayNode) p.get("dynamicValues"))
                                        .addObject()
                                        .put("fieldKey", e.getKey())
                                        .set("value", e.getValue()));
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0).put("fieldKey", "image");
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 1);
        var result = created(p, image(), image());
        long id = result.path("id").asLong();
        assertThat(result.size()).isEqualTo(4);
        assertThat(result.path("ticketNumber").asText()).matches("NX-\\d{4}-\\d{6,}");
        tx(
                () -> {
                    var t = em.find(Ticket.class, id);
                    assertThat(t.getReporter().getId()).isEqualTo(f.requester.getId());
                    assertThat(t.getPriority().name()).isEqualTo("HIGH");
                    assertThat(t.getRequiredSkill().getId()).isEqualTo(f.skill.getId());
                    assertThat(t.getSlaDueAt().toInstant())
                            .isEqualTo(t.getCreatedAt().plusMinutes(90).toInstant());
                    assertThat(t.getTitle()).isEqualTo("Máy chiếu không lên nguồn");
                    assertThat(t.getDescription()).isEqualTo("Mô tả dài\nchi tiết");
                    assertThat(
                                    em.createQuery(
                                                    "select h from TicketStatusHistory h where"
                                                            + " h.ticket.id=:id",
                                                    TicketStatusHistory.class)
                                            .setParameter("id", id)
                                            .getSingleResult()
                                            .getFromStatus())
                            .isNull();
                    return null;
                });
        var detail =
                mvc.perform(
                                get("/api/v1/tickets/{id}", id)
                                        .with(
                                                jwt().jwt(
                                                                j ->
                                                                        j.subject(
                                                                                f.requester
                                                                                        .getId()
                                                                                        .toString()))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        String body = detail.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body)
                .contains("REPORT_EVIDENCE", "SUBMIT", "false", "2026-10-18T09:30:00+07:00")
                .doesNotContain("storageKey", ROOT.toString());
        assertThat(json.readTree(body).path("dynamicValues")).hasSize(8);
        assertThat(
                        tx(
                                        () ->
                                                em.createQuery(
                                                                "select a from TicketAttachment a"
                                                                        + " where a.ticket.id=:id",
                                                                TicketAttachment.class)
                                                        .setParameter("id", id)
                                                        .getResultList())
                                .stream()
                                .filter(a -> a.getFieldDefinition() != null))
                .hasSize(1);
        assertThat(disk()).hasSize(2);
        mvc.perform(
                        get("/api/v1/tickets")
                                .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void missingDefaultsRemainNullAndLocationIsRequiredEvenWhenCategorySaysOptional()
            throws Exception {
        tx(
                () -> {
                    var form = em.find(CategoryFormVersion.class, f.form.getId());
                    form.changeDefaultSkillId(null);
                    form.changeDefaultSlaPolicyId(null);
                    return null;
                });
        var invalid = payload();
        invalid.remove("locationId");
        post(invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.locationId").exists());
        var result = created(payload());
        tx(
                () -> {
                    var ticket = em.find(Ticket.class, result.path("id").asLong());
                    assertThat(ticket.getRequiredSkill()).isNull();
                    assertThat(ticket.getSlaDueAt()).isNull();
                    return null;
                });
    }

    @Test
    void authenticatesAndUsesCurrentDatabaseRoles() throws Exception {
        mvc.perform(request(payload())).andExpect(status().isUnauthorized());
        mvc.perform(
                        request(payload())
                                .with(
                                        jwt().jwt(
                                                        j ->
                                                                j.subject(
                                                                                f.manager
                                                                                        .getId()
                                                                                        .toString())
                                                                        .claim(
                                                                                "roles",
                                                                                List.of(
                                                                                        "REQUESTER")))))
                .andExpect(status().isForbidden());
        tx(
                () -> {
                    em.find(AppUser.class, f.requester.getId()).changeStatus(UserStatus.DISABLED);
                    return null;
                });
        post(payload()).andExpect(status().isUnauthorized());
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "reporterId",
                "ticketNumber",
                "status",
                "priority",
                "requiredSkillId",
                "slaDueAt",
                "storageKey",
                "version",
                "unknown"
            })
    void rejectsClientManagedOrUnknownProperties(String property) throws Exception {
        post(payload().put(property, "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(count()).isZero();
    }

    @Test
    void rejectsWrongTypesAndMissingDuplicateOrMalformedTicketParts() throws Exception {
        for (String body : List.of("null", "[]", "{", "{\"title\":\"a\",\"title\":\"b\"}"))
            mvc.perform(
                            multipart("/api/v1/tickets")
                                    .part(ticket(body, "application/json"))
                                    .with(
                                            jwt().jwt(
                                                            j ->
                                                                    j.subject(
                                                                            f.requester
                                                                                    .getId()
                                                                                    .toString()))))
                    .andExpect(status().isBadRequest());
        post(payload().put("categoryId", "1")).andExpect(status().isBadRequest());
        post(payload().put("categoryId", 1.5)).andExpect(status().isBadRequest());
        mvc.perform(
                        request(payload())
                                .part(ticket(payload().toString(), "application/json"))
                                .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        multipart("/api/v1/tickets")
                                .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        multipart("/api/v1/tickets")
                                .part(ticket(payload().toString(), "text/plain"))
                                .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload().toString())
                                .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void enforcesCategoryFormLocationAndAssetPolicies() throws Exception {
        post(payload().put("categoryId", Long.MAX_VALUE)).andExpect(status().isNotFound());
        post(payload().put("formVersionId", Long.MAX_VALUE)).andExpect(status().isNotFound());
        post(payload().put("locationId", Long.MAX_VALUE)).andExpect(status().isNotFound());
        post(payload().put("assetId", Long.MAX_VALUE)).andExpect(status().isNotFound());
        post(payload().put("assetId", f.wrongAsset.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assetId").exists());
        tx(
                () -> {
                    em.find(IncidentCategory.class, f.category.getId())
                            .changeAssetPolicy(AssetPolicy.REQUIRED);
                    return null;
                });
        post(payload()).andExpect(status().isBadRequest());
        created(payload().put("assetId", f.asset.getId()));
        tx(
                () -> {
                    em.find(IncidentCategory.class, f.category.getId())
                            .changeAssetPolicy(AssetPolicy.FORBIDDEN);
                    return null;
                });
        post(payload().put("assetId", f.asset.getId())).andExpect(status().isBadRequest());
        tx(
                () -> {
                    em.find(CategoryFormVersion.class, f.form.getId())
                            .changeRequiresEquipment(true);
                    return null;
                });
        post(payload())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.type")
                                .value(
                                        "https://nexora.app/problems/form-configuration-incompatible"));
    }

    @Test
    void rejectsStaleFormAndInactiveReferences() throws Exception {
        tx(
                () -> {
                    em.find(com.nexora.location.entity.Location.class, f.location.getId())
                            .changeActive(false);
                    return null;
                });
        post(payload()).andExpect(status().isBadRequest());
        tx(
                () -> {
                    em.find(com.nexora.location.entity.Location.class, f.location.getId())
                            .changeActive(true);
                    em.find(Skill.class, f.skill.getId()).changeActive(false);
                    return null;
                });
        post(payload()).andExpect(status().isConflict());
        tx(
                () -> {
                    em.find(IncidentCategory.class, f.category.getId()).changeActive(false);
                    return null;
                });
        post(payload()).andExpect(status().isBadRequest());
        tx(
                () -> {
                    em.find(IncidentCategory.class, f.category.getId()).changeActive(true);
                    em.find(CategoryFormVersion.class, f.form.getId())
                            .changeFormStatus(FormStatus.ARCHIVED);
                    return null;
                });
        post(payload())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.type")
                                .value("https://nexora.app/problems/form-version-changed"));
    }

    @Test
    void rejectsLegacyImageAndLocalDatetimeRulesInsteadOfRepairingThem() throws Exception {
        for (var entry :
                Map.of(
                                "image",
                                "{\"allowedTypes\":[\"image/gif\"]}",
                                "datetime",
                                "{\"min\":\"2026-10-18T09:30:00\"}")
                        .entrySet()) {
            tx(
                    () -> {
                        em.find(FieldDefinition.class, f.fields.get(entry.getKey()).getId())
                                .changeValidationRules(entry.getValue());
                        return null;
                    });
            post(payload())
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(
                            jsonPath("$.type")
                                    .value(
                                            "https://nexora.app/problems/form-configuration-incompatible"));
            tx(
                    () -> {
                        em.find(FieldDefinition.class, f.fields.get(entry.getKey()).getId())
                                .changeValidationRules("{}");
                        return null;
                    });
        }
    }

    @Test
    void validatesValueTypesRequiredFieldsAndActiveOptions() throws Exception {
        for (var entry :
                Map.of(
                                "number",
                                json.getNodeFactory().textNode("0"),
                                "boolean",
                                json.getNodeFactory().textNode("false"),
                                "select",
                                json.getNodeFactory().textNode("old"),
                                "date",
                                json.getNodeFactory().textNode("2026-02-30"),
                                "datetime",
                                json.getNodeFactory().textNode("2026-10-18T09:30:00"),
                                "image",
                                json.getNodeFactory().textNode("scalar"))
                        .entrySet()) {
            var p = payload();
            ((ArrayNode) p.get("dynamicValues"))
                    .addObject()
                    .put("fieldKey", entry.getKey())
                    .set("value", entry.getValue());
            post(p).andExpect(status().isBadRequest());
        }
        tx(
                () -> {
                    em.find(FieldDefinition.class, f.fields.get("text").getId())
                            .changeRequired(true);
                    return null;
                });
        post(payload())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['dynamicValues.text']").exists());
        tx(
                () -> {
                    em.find(FieldDefinition.class, f.fields.get("text").getId())
                            .changeRequired(false);
                    em.find(FieldDefinition.class, f.fields.get("number").getId())
                            .changeActive(false);
                    return null;
                });
        var p = payload();
        ((ArrayNode) p.get("dynamicValues")).addObject().put("fieldKey", "number").put("value", 0);
        post(p).andExpect(status().isBadRequest());
    }

    @Test
    void validatesFileMappingMimeSizeAndSignatureBeforeAnyWrites() throws Exception {
        var p = payload();
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0);
        post(p, image("empty.png", "image/png", new byte[0])).andExpect(status().isBadRequest());
        post(p, image("bad.png", "image/png", new byte[] {1, 2, 3}))
                .andExpect(status().isBadRequest());
        post(p, image("bad.gif", "image/gif", new byte[] {1, 2, 3}))
                .andExpect(status().isUnsupportedMediaType());
        post(p, image("big.png", "image/png", new byte[5242881]))
                .andExpect(status().isPayloadTooLarge());
        post(payload(), image()).andExpect(status().isBadRequest());
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0);
        post(p, image(), image()).andExpect(status().isBadRequest());
        verify(storage, never()).store(any());
        assertThat(count()).isZero();
        assertThat(disk()).isEmpty();
    }

    @Test
    void cleansFirstImageIfSecondStoreFailsButNeverDeletesCommittedEvidence() throws Exception {
        var p = payload();
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0);
        created(p, image());
        Set<Path> before = disk();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (calls.incrementAndGet() == 2)
                                throw new FileStorageException(
                                        "simulated second store failure",
                                        new java.io.IOException("test-only"));
                            return invocation.callRealMethod();
                        })
                .when(storage)
                .store(any());
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 1);
        post(p, image(), image()).andExpect(status().isInternalServerError());
        assertThat(count()).isEqualTo(1);
        assertThat(disk()).isEqualTo(before);
    }

    @Test
    void databaseFailureAfterStorageRollsBackEveryRowAndDeletesRequestImages() throws Exception {
        doAnswer(
                        invocation -> {
                            StoredFile stored = (StoredFile) invocation.callRealMethod();
                            return new StoredFile(
                                    stored.storageKey(),
                                    "x".repeat(256),
                                    stored.contentType(),
                                    stored.size());
                        })
                .when(storage)
                .store(any());
        var p = payload();
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0);
        post(p, image()).andExpect(status().isInternalServerError());
        assertThat(count()).isZero();
        assertThat(disk()).isEmpty();
        assertThat(
                        tx(
                                () ->
                                        em.createQuery(
                                                        "select count(v) from TicketFieldValue v"
                                                                + " where v.ticket.category.id=:id",
                                                        Long.class)
                                                .setParameter("id", f.category.getId())
                                                .getSingleResult()))
                .isZero();
    }

    @Test
    void concurrentSubmissionsHaveDifferentPersistedNumbers() throws Exception {
        var p = payload();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> created(p));
            var two = executor.submit(() -> created(p));
            assertThat(one.get(30, TimeUnit.SECONDS).path("ticketNumber").asText())
                    .isNotEqualTo(two.get(30, TimeUnit.SECONDS).path("ticketNumber").asText());
            assertThat(count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void openApiDocumentsMultipartPartsAndResponseErrors() throws Exception {
        JsonNode doc =
                json.readTree(
                        mvc.perform(get("/v3/api-docs"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray());
        var api = doc.path("paths").path("/api/v1/tickets").path("post");
        assertThat(api.path("requestBody").path("content").has("multipart/form-data")).isTrue();
        assertThat(api.path("responses").properties().stream().map(Map.Entry::getKey).toList())
                .contains("201", "400", "401", "403", "404", "409", "413", "415", "422");
    }

    @Test
    void holdsCategorySnapshotUntilCommitAndKeepsHistoricalFormAfterRepublish() throws Exception {
        var stored = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            Object file = invocation.callRealMethod();
                            stored.countDown();
                            if (!finish.await(15, TimeUnit.SECONDS))
                                throw new IllegalStateException("test wait timed out");
                            return file;
                        })
                .when(storage)
                .store(any());
        var executor = Executors.newFixedThreadPool(2);
        var p = payload();
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0);
        try {
            var submission = executor.submit(() -> created(p, image()));
            assertThat(stored.await(15, TimeUnit.SECONDS)).isTrue();
            var updating = new CountDownLatch(1);
            var publication =
                    executor.submit(
                            () ->
                                    tx(
                                            () -> {
                                                updating.countDown();
                                                var category =
                                                        em.find(
                                                                IncidentCategory.class,
                                                                f.category.getId(),
                                                                jakarta.persistence.LockModeType
                                                                        .PESSIMISTIC_WRITE);
                                                var old =
                                                        em.find(
                                                                CategoryFormVersion.class,
                                                                f.form.getId());
                                                old.changeFormStatus(FormStatus.ARCHIVED);
                                                em.flush();
                                                var next = new CategoryFormVersion(category, 2);
                                                next.changeFormStatus(FormStatus.PUBLISHED);
                                                next.changePublishingMetadata(
                                                        f.manager.getId(),
                                                        java.time.OffsetDateTime.now());
                                                f.persist(next);
                                                category.changeDefaultPriority(IssuePriority.LOW);
                                                return null;
                                            }));
            assertThat(updating.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> publication.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            finish.countDown();
            var result = submission.get(20, TimeUnit.SECONDS);
            publication.get(20, TimeUnit.SECONDS);
            tx(
                    () -> {
                        var ticket = em.find(Ticket.class, result.path("id").asLong());
                        assertThat(ticket.getFormVersion().getId()).isEqualTo(f.form.getId());
                        assertThat(ticket.getPriority().name()).isEqualTo("HIGH");
                        return null;
                    });
            mvc.perform(
                            get("/api/v1/tickets/{id}", result.path("id").asLong())
                                    .with(
                                            jwt().jwt(
                                                            j ->
                                                                    j.subject(
                                                                            f.requester
                                                                                    .getId()
                                                                                    .toString()))))
                    .andExpect(status().isOk());
            post(payload()).andExpect(status().isConflict());
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void validatesRequiredImageAndFieldLimitsAndDuplicateValues() throws Exception {
        tx(
                () -> {
                    var field = em.find(FieldDefinition.class, f.fields.get("image").getId());
                    field.changeRequired(true);
                    field.changeValidationRules(
                            "{\"minFiles\":1,\"maxFiles\":1,\"maxSizeBytes\":8}");
                    return null;
                });
        post(payload()).andExpect(status().isBadRequest());
        var p = payload();
        ((ArrayNode) p.get("attachments")).addObject().put("fileIndex", 0).put("fieldKey", "image");
        post(p, image()).andExpect(status().isPayloadTooLarge());
        tx(
                () -> {
                    em.find(FieldDefinition.class, f.fields.get("image").getId())
                            .changeRequired(false);
                    em.find(FieldDefinition.class, f.fields.get("image").getId())
                            .changeValidationRules("{}");
                    return null;
                });
        p = payload();
        ((ArrayNode) p.get("dynamicValues")).addObject().put("fieldKey", "number").put("value", 0);
        ((ArrayNode) p.get("dynamicValues")).addObject().put("fieldKey", "number").put("value", 0);
        post(p).andExpect(status().isBadRequest());
        post(payload().put("title", "x".repeat(201))).andExpect(status().isBadRequest());
        post(payload().put("description", "  ")).andExpect(status().isBadRequest());
    }

    @Test
    void actualHttpMultipartRequestHonorsContainerSizeLimits() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                port > 0, "Container multipart parsing is exercised by the HTTP H2 suite.");
        String token = tokens.buildToken(f.requester.getId(), List.of("REQUESTER"));
        for (int count : List.of(0, 1, 6)) {
            String boundary = "nexora-test-" + UUID.randomUUID();
            var body = new ArrayList<byte[]>();
            body.add(
                    ("--"
                                    + boundary
                                    + "\r\n"
                                    + "Content-Disposition: form-data; name=\"ticket\"\r\n"
                                    + "Content-Type: application/json\r\n\r\n"
                                    + payload()
                                    + "\r\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] bytes = new byte[count == 1 ? 5242881 : 5242880];
            for (int i = 0; i < count; i++) {
                body.add(
                        ("--"
                                        + boundary
                                        + "\r\n"
                                        + "Content-Disposition: form-data; name=\"files\";"
                                        + " filename=\"photo.png\"\r\n"
                                        + "Content-Type: image/png\r\n\r\n")
                                .getBytes());
                body.add(bytes);
                body.add("\r\n".getBytes());
            }
            body.add(("--" + boundary + "--\r\n").getBytes());
            var request =
                    java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create(
                                            "http://localhost:" + port + "/api/v1/tickets"))
                            .timeout(java.time.Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArrays(body))
                            .build();
            var response =
                    java.net.http.HttpClient.newHttpClient()
                            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (count == 0) {
                assertThat(response.statusCode()).isEqualTo(201);
                var created = json.readTree(response.body());
                var detailRequest =
                        java.net.http.HttpRequest.newBuilder(
                                        java.net.URI.create(
                                                "http://localhost:"
                                                        + port
                                                        + "/api/v1/tickets/"
                                                        + created.path("id").asLong()))
                                .header("Authorization", "Bearer " + token)
                                .GET()
                                .build();
                var detail =
                        java.net.http.HttpClient.newHttpClient()
                                .send(
                                        detailRequest,
                                        java.net.http.HttpResponse.BodyHandlers.ofString());
                assertThat(detail.statusCode()).isEqualTo(200);
                assertThat(detail.body()).contains(created.path("ticketNumber").asText(), "SUBMIT");
                continue;
            }
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .contains("application/problem+json");
            assertThat(json.readTree(response.body()).path("instance").asText())
                    .isEqualTo("/api/v1/tickets");
        }
        assertThat(count()).isEqualTo(1);
        assertThat(disk()).isEmpty();
    }

    @Test
    void enforcesBoundsStepPatternsAndOffsetDatetimeRulesWithoutCoercingValues() throws Exception {
        tx(
                () -> {
                    em.find(FieldDefinition.class, f.fields.get("number").getId())
                            .changeValidationRules("{\"min\":0,\"max\":10,\"step\":0.5}");
                    em.find(FieldDefinition.class, f.fields.get("text").getId())
                            .changeValidationRules(
                                    "{\"minLength\":2,\"maxLength\":4,\"pattern\":\"[A-Z]+\"}");
                    em.find(FieldDefinition.class, f.fields.get("datetime").getId())
                            .changeValidationRules(
                                    "{\"min\":\"2026-10-18T09:00:00+07:00\",\"max\":\"2026-10-18T10:00:00+07:00\"}");
                    return null;
                });
        for (var value :
                List.of(
                        json.getNodeFactory().numberNode(-1),
                        json.getNodeFactory().numberNode(11),
                        json.getNodeFactory().numberNode(0.3))) {
            var p = payload();
            ((ArrayNode) p.get("dynamicValues"))
                    .addObject()
                    .put("fieldKey", "number")
                    .set("value", value);
            post(p).andExpect(status().isBadRequest());
        }
        for (String value : List.of("a", "ABCDX", "ab")) {
            var p = payload();
            ((ArrayNode) p.get("dynamicValues"))
                    .addObject()
                    .put("fieldKey", "text")
                    .put("value", value);
            post(p).andExpect(status().isBadRequest());
        }
        var invalid = payload();
        ((ArrayNode) invalid.get("dynamicValues"))
                .addObject()
                .put("fieldKey", "datetime")
                .put("value", "2026-10-18T01:00:00Z");
        post(invalid).andExpect(status().isBadRequest());
        var valid = payload();
        ((ArrayNode) valid.get("dynamicValues"))
                .addObject()
                .put("fieldKey", "number")
                .put("value", 0.5);
        ((ArrayNode) valid.get("dynamicValues"))
                .addObject()
                .put("fieldKey", "text")
                .put("value", "AB");
        ((ArrayNode) valid.get("dynamicValues"))
                .addObject()
                .put("fieldKey", "datetime")
                .put("value", "2026-10-18T02:30:00Z");
        created(valid);
    }

    @Test
    void optionalEmptyAnswersProduceNoRowsAndForeignCategoryIsNotStale() throws Exception {
        var p = payload();
        for (String key : List.of("text", "number", "boolean", "multi_select"))
            ((ArrayNode) p.get("dynamicValues")).addObject().put("fieldKey", key).putNull("value");
        var id = created(p).path("id").asLong();
        assertThat(
                        tx(
                                () ->
                                        em.createQuery(
                                                        "select count(v) from TicketFieldValue v"
                                                            + " where v.ticket.id=:id",
                                                        Long.class)
                                                .setParameter("id", id)
                                                .getSingleResult()))
                .isZero();
        Long otherCategory =
                tx(
                        () ->
                                f.persist(
                                                new IncidentCategory(
                                                        "OTHER-CATEGORY-"
                                                                + UUID.randomUUID()
                                                                        .toString()
                                                                        .substring(0, 8),
                                                        "Other"))
                                        .getId());
        post(payload().put("categoryId", otherCategory))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.formVersionId").exists());
    }
}
