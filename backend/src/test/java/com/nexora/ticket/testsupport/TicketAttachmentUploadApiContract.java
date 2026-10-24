package com.nexora.ticket.testsupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexora.auth.security.JwtTokenBuilder;
import com.nexora.common.storage.*;
import com.nexora.ticket.entity.*;
import com.nexora.ticket.repository.*;
import com.nexora.user.entity.*;

import jakarta.persistence.EntityManager;

/** Same multipart, authorization, transaction and storage contract on H2 and SQL Server. */
public abstract class TicketAttachmentUploadApiContract {
    private static final String METADATA = "{\"attachmentType\":\"WORK_RESULT\"}";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final Path ROOT;
    static {
        try { ROOT = Files.createTempDirectory("nexora-result-upload-"); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    @Autowired protected ObjectMapper json;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected TicketRepository tickets;
    @Autowired protected TicketAssignmentRepository assignments;
    @Autowired protected JwtTokenBuilder tokens;
    @MockitoSpyBean protected FileStorageService storage;
    @MockitoSpyBean protected TicketAttachmentRepository attachments;
    @Value("${local.server.port:0}") protected int port;
    protected TicketReadFixtures f;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.root", ROOT::toString);
    }

    @BeforeEach
    void setup() {
        tx(() -> {
            f = new TicketReadFixtures(em).create();
            em.find(Ticket.class, f.current.getId()).changeStatus(TicketStatus.IN_PROGRESS);
            return null;
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        reset(storage, attachments);
        if (f != null) tx(() -> {
            for (String entity : List.of("WorkLog", "TicketAttachment", "TicketStatusHistory"))
                em.createQuery("delete from " + entity + " x where x.ticket.id in :ids")
                        .setParameter("ids", f.ticketIds()).executeUpdate();
            em.createQuery("delete from UserRole r where r.user.id=:id")
                    .setParameter("id", f.technician.getId()).executeUpdate();
            f.cleanup();
            return null;
        });
        for (Path file : disk()) Files.deleteIfExists(file);
    }

    protected <T> T tx(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    private Set<Path> disk() throws Exception {
        try (var paths = Files.list(ROOT)) { return paths.collect(java.util.stream.Collectors.toSet()); }
    }

    private MockPart image() { return image("photo.png", "image/png", PNG); }

    private MockPart image(String name, String mime, byte[] bytes) {
        var part = new MockPart("files", name, bytes);
        if (mime != null) part.getHeaders().setContentType(MediaType.parseMediaType(mime));
        return part;
    }

    private MockPart metadata(String value) {
        return new MockPart("metadata", value.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions upload(Long id, AppUser actor, String metadata, MockPart... files) throws Exception {
        var request = multipart("/api/v1/tickets/{id}/attachments", id).part(metadata(metadata));
        for (var file : files) request.part(file);
        if (actor != null) request.with(jwt().jwt(j -> j.subject(actor.getId().toString())));
        return mvc.perform(request);
    }

    private ResultActions upload(MockPart... files) throws Exception {
        return upload(f.current.getId(), f.technician, METADATA, files);
    }

    private ObjectNode detail() throws Exception {
        return (ObjectNode) json.readTree(mvc.perform(get("/api/v1/tickets/{id}", f.current.getId())
                        .with(jwt().jwt(j -> j.subject(f.manager.getId().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private void problem(ResultActions result, int code) throws Exception {
        result.andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void savesBatchAndRefreshesAuthorizedImagesWithoutChangingTicket(int count) throws Exception {
        tx(() -> {
            em.persist(new WorkLog(em.find(Ticket.class, f.current.getId()), f.technician,
                    WorkLogType.REPAIR, "Existing work", TicketReadFixtures.TIME, null, null, TicketReadFixtures.TIME));
            return null;
        });
        ObjectNode before = detail();
        var files = new MockPart[count];
        for (int i = 0; i < count; i++) files[i] = i % 2 == 0 ? image()
                : image("result.jpg", "image/jpeg", JPEG);
        JsonNode created = json.readTree(upload(files).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(created.size()).isEqualTo(count);
        ObjectNode after = detail();
        assertThat(after.get("attachments")).isEqualTo(created);
        before.remove("attachments");
        after.remove("attachments");
        assertThat(after).isEqualTo(before);
        for (var entry : created) {
            assertThat(entry.size()).isEqualTo(8);
            assertThat(entry.path("attachmentType").asText()).isEqualTo("WORK_RESULT");
            assertThat(entry.path("fieldDefinitionId").isNull()).isTrue();
            assertThat(entry.at("/uploadedBy/id").asLong()).isEqualTo(f.technician.getId());
            OffsetDateTime.parse(entry.path("createdAt").asText());
            tx(() -> {
                var row = attachments.findById(entry.path("id").asLong()).orElseThrow();
                assertThat(row.getFormVersionId()).isEqualTo(f.form.getId());
                assertThat(row.getTicket().getId()).isEqualTo(f.current.getId());
                assertThat(row.getStorageKey()).matches("[0-9a-f-]{36}");
                return null;
            });
            for (AppUser viewer : List.of(f.other, f.technician, f.multi, f.manager, f.admin)) {
                byte[] expected = entry.path("contentType").asText().equals("image/png") ? PNG : JPEG;
                mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/content",
                                f.current.getId(), entry.path("id").asLong())
                                .with(jwt().jwt(j -> j.subject(viewer.getId().toString()))))
                        .andExpect(status().isOk()).andExpect(content().bytes(expected))
                        .andExpect(header().exists("Content-Disposition"));
            }
            mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/content",
                            f.current.getId(), entry.path("id").asLong())
                            .with(jwt().jwt(j -> j.subject(f.requester.getId().toString()))))
                    .andExpect(status().isNotFound());
        }
        assertThat(disk()).hasSize(count);
        assertThat(tx(() -> em.createQuery("select count(w) from WorkLog w where w.ticket.id=:id", Long.class)
                .setParameter("id", f.current.getId()).getSingleResult())).isEqualTo(1);
    }

    @Test
    void usesCurrentDatabaseIdentityAndRoleUnionWithoutManagementOverride() throws Exception {
        for (var actor : List.of(f.other, f.manager, f.admin, f.multi))
            problem(upload(f.current.getId(), actor, METADATA, image()), 403);
        for (var actor : List.of(f.requester, f.noRole))
            problem(upload(f.current.getId(), actor, METADATA, image()), 404);
        problem(upload(f.hidden.getId(), f.technician, METADATA, image()), 404);
        problem(upload(Long.MAX_VALUE, f.manager, METADATA, image()), 404);
        problem(upload(f.historical.getId(), f.technician, METADATA, image()), 403);
        problem(upload(f.current.getId(), null, METADATA, image()), 401);
        mvc.perform(multipart("/api/v1/tickets/{id}/attachments", f.current.getId())
                        .part(metadata(METADATA), image()).with(jwt().jwt(j -> j.subject("9223372036854775807"))))
                .andExpect(status().isUnauthorized());
        tx(() -> {
            var role = em.createQuery("select r from Role r where r.code=:code", Role.class)
                    .setParameter("code", RoleCode.ADMIN).getSingleResult();
            em.persist(new UserRole(em.find(AppUser.class, f.technician.getId()), role, null));
            return null;
        });
        upload(image()).andExpect(status().isCreated());
        tx(() -> { em.find(AppUser.class, f.technician.getId()).changeStatus(UserStatus.DISABLED); return null; });
        problem(upload(image()), 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "RESOLVED", "CLOSED", "REJECTED", "CANCELLED", "REOPENED"})
    void rejectsOtherStatesAfterVisibilityAndOwnership(String status) throws Exception {
        tx(() -> { em.find(Ticket.class, f.current.getId()).changeStatus(TicketStatus.valueOf(status)); return null; });
        problem(upload(image()), 422);
        problem(upload(f.current.getId(), f.manager, METADATA, image()), 403);
        problem(upload(f.current.getId(), f.requester, METADATA, image()), 404);
        verify(storage, never()).store(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "1", "\"WORK_RESULT\"", "{}",
            "{\"attachmentType\":null}", "{\"attachmentType\":0}", "{\"attachmentType\":true}",
            "{\"attachmentType\":[\"WORK_RESULT\"]}", "{\"attachmentType\":\"REPORT_EVIDENCE\"}",
            "{\"attachmentType\":\"COMMENT\"}", "{\"attachmentType\":\"work_result\"}",
            "{\"attachmentType\":\"WORK_RESULT\",\"ticketId\":1}",
            "{\"attachmentType\":\"WORK_RESULT\",\"uploadedBy\":1}",
            "{\"attachmentType\":\"WORK_RESULT\",\"storageKey\":\"x\"}",
            "{\"attachmentType\":\"WORK_RESULT\",\"version\":0}",
            "{\"attachmentType\":\"WORK_RESULT\",\"attachmentType\":\"WORK_RESULT\"}",
            "{\"attachmentType\":\"WORK_RESULT\"} {}", "{"})
    void rejectsMetadataOutsideContract(String metadata) throws Exception {
        problem(upload(f.current.getId(), f.technician, metadata, image()), 400);
        verify(storage, never()).store(any());
    }

    @Test
    void rejectsInvalidPathPartsAndRequestContentType() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808"))
            problem(mvc.perform(multipart("/api/v1/tickets/" + id + "/attachments")
                    .part(metadata(METADATA), image()).with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))), 400);
        for (MockPart[] parts : List.of(new MockPart[] {image()},
                new MockPart[] {metadata(METADATA), metadata(METADATA), image()},
                new MockPart[] {metadata(METADATA), new MockPart("uploadedBy", new byte[] {1}), image()}))
            problem(mvc.perform(multipart("/api/v1/tickets/{id}/attachments", f.current.getId()).part(parts)
                    .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))), 400);
        problem(mvc.perform(post("/api/v1/tickets/{id}/attachments", f.current.getId())
                .contentType(MediaType.APPLICATION_JSON).content(METADATA)
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))), 415);
    }

    @Test
    void validatesEntireBatchBeforeStorageAndAcceptsExactSizeLimit() throws Exception {
        problem(upload(), 400);
        problem(upload(image(), image(), image(), image(), image(), image()), 400);
        problem(upload(image(), image("empty.png", "image/png", new byte[0])), 400);
        problem(upload(image("fake.png", "image/png", JPEG)), 400);
        problem(upload(image("fake.jpg", "image/jpeg", PNG)), 400);
        problem(upload(image("short.png", "image/png", new byte[] {(byte) 0x89})), 400);
        problem(upload(image("gif.gif", "image/gif", PNG)), 415);
        problem(upload(image("missing.png", null, PNG)), 415);
        problem(upload(image("large.png", "image/png", new byte[5242881])), 413);
        problem(upload(image(" ", "image/png", PNG)), 400);
        problem(upload(image("x".repeat(256), "image/png", PNG)), 400);
        verify(storage, never()).store(any());
        byte[] maximum = Arrays.copyOf(PNG, 5242880);
        upload(image("max.png", "image/png", maximum)).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cleansOnlyThisBatchOnStorageOrPersistenceFailure(boolean persistenceFailure) throws Exception {
        upload(image()).andExpect(status().isCreated());
        Set<Path> beforeDisk = disk();
        ObjectNode before = detail();
        if (persistenceFailure) doThrow(new DataIntegrityViolationException("test metadata failure"))
                .when(attachments).flush();
        else {
            var calls = new AtomicInteger();
            doAnswer(call -> {
                if (calls.incrementAndGet() == 2) throw new FileStorageException("test storage failure", null);
                return call.callRealMethod();
            }).when(storage).store(any());
        }
        problem(upload(image(), image()), 500);
        assertThat(detail()).isEqualTo(before);
        assertThat(disk()).isEqualTo(beforeDisk);
    }

    private void saveLog() {
        tx(() -> {
            em.persist(new WorkLog(em.find(Ticket.class, f.current.getId()), f.technician,
                    WorkLogType.REPAIR, "Repaired", TicketReadFixtures.TIME, null, null, TicketReadFixtures.TIME));
            return null;
        });
    }

    private ResultActions resolve() throws Exception {
        return mvc.perform(post("/api/v1/tickets/{id}/resolve", f.current.getId())
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + detail().path("version").asLong()
                        + ",\"resolutionOutcome\":\"FIXED\",\"resolutionSummary\":\"Repaired\"}"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completeStartWorkUploadResolveReadCloseJourneyWithOptionalImages(boolean withImages) throws Exception {
        tx(() -> { em.find(Ticket.class, f.current.getId()).changeStatus(TicketStatus.ASSIGNED); return null; });
        long version = detail().path("version").asLong();
        mvc.perform(post("/api/v1/tickets/{id}/start", f.current.getId())
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andExpect(status().isOk());
        version = detail().path("version").asLong();
        mvc.perform(post("/api/v1/tickets/{id}/work-logs", f.current.getId())
                .with(jwt().jwt(j -> j.subject(f.technician.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version
                        + ",\"logType\":\"REPAIR\",\"content\":\"Repaired\",\"startedAt\":\"2026-10-08T08:30:00+07:00\"}"))
                .andExpect(status().isCreated());
        if (withImages) upload(image()).andExpect(status().isCreated());
        resolve().andExpect(status().isOk());
        if (withImages) mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/content",
                        f.current.getId(), detail().at("/attachments/0/id").asLong())
                        .with(jwt().jwt(j -> j.subject(f.other.getId().toString()))))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        version = detail().path("version").asLong();
        mvc.perform(post("/api/v1/tickets/{id}/close", f.current.getId())
                .with(jwt().jwt(j -> j.subject(f.other.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andExpect(status().isOk());
        assertThat(detail().path("status").asText()).isEqualTo("CLOSED");
        problem(upload(image()), 403);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uploadWaitsAndRechecksAfterResolveOrAssignmentChange(boolean changeAssignment) throws Exception {
        saveLog();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var change = executor.submit(() -> tx(() -> {
                var ticket = tickets.findByIdForUpdate(f.current.getId()).orElseThrow();
                var current = assignments.findByTicketIdAndEndedAtIsNull(ticket.getId()).orElseThrow();
                current.end(OffsetDateTime.now(), changeAssignment ? "REASSIGNED" : "RESOLVED");
                em.flush();
                if (changeAssignment) em.persist(new TicketAssignment(ticket.getId(), f.multi.getId(), f.manager.getId(), OffsetDateTime.now()));
                else ticket.resolve(ResolutionOutcome.FIXED, "Repaired", OffsetDateTime.now());
                locked.countDown();
                await(release);
                return null;
            }));
            assertThat(locked.await(15, TimeUnit.SECONDS)).isTrue();
            var upload = executor.submit(() -> { attempted.countDown(); return upload(image()); });
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> upload.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            change.get(20, TimeUnit.SECONDS);
            problem(upload.get(20, TimeUnit.SECONDS), 403);
            assertThat(disk()).isEmpty();
            assertThat(detail().path("attachments").size()).isZero();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uploadHoldsTicketLockUntilFilesAndMetadataCommit(boolean changeAssignment) throws Exception {
        saveLog();
        var stored = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        doAnswer(call -> {
            var result = call.callRealMethod();
            stored.countDown();
            await(release);
            return result;
        }).when(storage).store(any());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var upload = executor.submit(() -> upload(image()));
            assertThat(stored.await(15, TimeUnit.SECONDS)).isTrue();
            var change = executor.submit(() -> {
                attempted.countDown();
                return tx(() -> {
                    var ticket = tickets.findByIdForUpdate(f.current.getId()).orElseThrow();
                    var assignment = assignments.findByTicketIdAndEndedAtIsNull(ticket.getId()).orElseThrow();
                    assignment.end(OffsetDateTime.now(), changeAssignment ? "REASSIGNED" : "RESOLVED");
                    em.flush();
                    if (changeAssignment) em.persist(new TicketAssignment(ticket.getId(), f.multi.getId(), f.manager.getId(), OffsetDateTime.now()));
                    else ticket.resolve(ResolutionOutcome.FIXED, "Repaired", OffsetDateTime.now());
                    return null;
                });
            });
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> change.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            upload.get(20, TimeUnit.SECONDS).andExpect(status().isCreated());
            change.get(20, TimeUnit.SECONDS);
            assertThat(detail().path("attachments").size()).isEqualTo(1);
            problem(upload(image()), 403);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for competing transaction"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }

    @Test
    void realHttpMultipartHonorsFileAndRequestLimits() throws Exception {
        Assumptions.assumeTrue(port > 0, "Embedded HTTP parsing runs in the H2 suite.");
        String token = tokens.buildToken(f.technician.getId(), List.of("TECHNICIAN"));
        for (int count : List.of(1, 6)) {
            String boundary = "nexora-upload-" + UUID.randomUUID();
            var body = new ArrayList<byte[]>();
            body.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\n\r\n"
                    + METADATA + "\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] bytes = new byte[count == 1 ? 5242881 : 5242880];
            for (int i = 0; i < count; i++) {
                body.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"image.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.add(bytes);
                body.add("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port
                            + "/api/v1/tickets/" + f.current.getId() + "/attachments"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArrays(body)).build();
            var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }
        assertThat(disk()).isEmpty();
    }
}
