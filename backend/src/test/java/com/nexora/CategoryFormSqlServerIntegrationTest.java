package com.nexora;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.*;
import com.nexora.category.repository.*;
import com.nexora.category.service.CategoryService;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.user.repository.SkillRepository;
import com.nexora.sla.repository.SlaPolicyRepository;

/** Real SQL Server checks for row locks, the filtered unique index and transaction rollback. */
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class CategoryFormSqlServerIntegrationTest {
    @Autowired CategoryService service;
    @Autowired IncidentCategoryRepository categories;
    @Autowired CategoryFormVersionRepository forms;
    @Autowired SkillRepository skills;
    @Autowired SlaPolicyRepository slas;
    @Autowired JdbcTemplate jdbc;
    Long categoryId;

    @BeforeEach void setup() {
        categoryId = service.createCategory(new CategoryRequest("FORM_IT_" + UUID.randomUUID().toString().replace("-", ""),
                "Form integration test", null, AssetPolicy.OPTIONAL, true, IssuePriority.NORMAL, true), null).id();
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM dbo.field_options WHERE field_definition_id IN (SELECT id FROM dbo.field_definitions WHERE form_version_id IN (SELECT id FROM dbo.category_form_versions WHERE category_id = ?))", categoryId);
        jdbc.update("DELETE FROM dbo.field_definitions WHERE form_version_id IN (SELECT id FROM dbo.category_form_versions WHERE category_id = ?)", categoryId);
        jdbc.update("DELETE FROM dbo.category_form_versions WHERE category_id = ?", categoryId);
        jdbc.update("DELETE FROM dbo.incident_categories WHERE id = ?", categoryId);
    }

    FormResponse draft() {
        FormResponse f = service.createDraft(categoryId, null);
        return service.updateDraft(f.id(), new DraftRequest(f.revision(), false, 0,
                skills.findActiveOrderByNameAndCode().getFirst().getId(), slas.findByActiveTrueOrderByPolicyName().getFirst().getId(), List.of()));
    }

    @Test void concurrentPublishesOfSameDraftHaveOneWinnerAndOneStaleWrite() throws Exception {
        FormResponse f = draft();
        List<Object> outcomes = race(() -> service.publish(f.id(), f.revision(), null), () -> service.publish(f.id(), f.revision(), null));
        assertThat(outcomes.stream().filter(FormResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(StaleWriteException.class::isInstance)).hasSize(1);
        assertOnePublished();
    }

    @Test void concurrentPublishesOfDifferentDraftsNeverLeaveTwoPublishedForms() throws Exception {
        FormResponse first = draft(), second = draft();
        List<Object> outcomes = race(() -> service.publish(first.id(), first.revision(), null), () -> service.publish(second.id(), second.revision(), null));
        assertThat(outcomes).allMatch(FormResponse.class::isInstance);
        assertOnePublished();
        assertThat(forms.findByCategoryIdOrderByVersionNumberAsc(categoryId)).extracting(CategoryFormVersion::getFormStatus)
                .containsExactlyInAnyOrder(FormStatus.PUBLISHED, FormStatus.ARCHIVED);
    }

    @Test void concurrentDraftCreationUsesDistinctServerAssignedVersions() throws Exception {
        List<Object> outcomes = race(() -> service.createDraft(categoryId, null), () -> service.createDraft(categoryId, null));
        assertThat(outcomes).allMatch(FormResponse.class::isInstance);
        assertThat(forms.findByCategoryIdOrderByVersionNumberAsc(categoryId)).extracting(CategoryFormVersion::getVersionNumber).containsExactly(1, 2);
    }

    @Test void failureAfterArchivingOldFormRollsBackTheEntirePublication() {
        FormResponse old = draft(); service.publish(old.id(), old.revision(), null);
        FormResponse next = draft();
        String trigger = "test_reject_form_" + next.id();
        jdbc.execute("CREATE TRIGGER dbo." + trigger + " ON dbo.category_form_versions AFTER UPDATE AS BEGIN IF EXISTS (SELECT 1 FROM inserted WHERE id = " + next.id() + " AND form_status = 'PUBLISHED') THROW 51000, 'Injected publication failure', 1; END");
        try {
            assertThatThrownBy(() -> service.publish(next.id(), next.revision(), null)).isInstanceOf(RuntimeException.class);
            assertThat(service.published(categoryId).id()).isEqualTo(old.id());
            assertThat(service.form(next.id()).status()).isEqualTo(FormStatus.DRAFT);
            assertOnePublished();
        } finally { jdbc.execute("DROP TRIGGER dbo." + trigger); }
    }

    @Test void draftReplacementWithOptionsUsesRealSqlServerForeignKeys() {
        FormResponse f = draft();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        FieldRequest field = new FieldRequest("choice", "Choice", DynamicFieldType.SELECT, null, null, true, true, 0,
                json.createObjectNode(), List.of(new OptionRequest("a", "A", 0, true)));
        f = service.updateDraft(f.id(), new DraftRequest(f.revision(), false, 0, f.defaultSkillId(), f.defaultSlaPolicyId(), List.of(field)));
        assertThat(f.fields().getFirst().options()).hasSize(1);
        f = service.updateDraft(f.id(), new DraftRequest(f.revision(), false, 0, f.defaultSkillId(), f.defaultSlaPolicyId(), List.of()));
        assertThat(f.fields()).isEmpty();
    }

    void assertOnePublished() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.category_form_versions WHERE category_id = ? AND form_status = 'PUBLISHED'", Integer.class, categoryId)).isEqualTo(1);
    }

    List<Object> race(Supplier<FormResponse> a, Supplier<FormResponse> b) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> results = List.of(executor.submit(() -> run(a, ready, start)), executor.submit(() -> run(b, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(results.getFirst().get(30, TimeUnit.SECONDS), results.getLast().get(30, TimeUnit.SECONDS));
        }
    }

    Object run(Supplier<FormResponse> action, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); start.await();
        try { return action.get(); } catch (RuntimeException e) { return e; }
    }
}
