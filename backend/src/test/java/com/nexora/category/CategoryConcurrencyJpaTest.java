package com.nexora.category;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.nexora.category.dto.CategoryContracts.*;
import com.nexora.category.entity.*;
import com.nexora.category.repository.*;
import com.nexora.category.service.*;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.sla.repository.SlaPolicyRepository;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.SkillRepository;
import com.nexora.testsupport.NexoraJpaTest;

@NexoraJpaTest
@Import({CategoryService.class, FormValidator.class, CategoryServiceJpaTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CategoryConcurrencyJpaTest {
    @Autowired CategoryService service;
    @Autowired IncidentCategoryRepository categories;
    @Autowired CategoryFormVersionRepository forms;
    @Autowired SkillRepository skills;
    @Autowired SlaPolicyRepository slas;
    Long categoryId, skillId, slaId;

    @BeforeEach void setup() {
        categoryId = categories.saveAndFlush(new IncidentCategory("RACE_" + UUID.randomUUID().toString().replace("-", ""), "Race test")).getId();
        skillId = skills.saveAndFlush(new Skill("RACE_SKILL", "Race skill")).getId();
        slaId = slas.saveAndFlush(new SlaPolicy("Race SLA", IssuePriority.NORMAL, 60)).getId();
    }
    @AfterEach void cleanup() {
        forms.deleteAll(forms.findByCategoryIdOrderByVersionNumberAsc(categoryId));
        categories.deleteById(categoryId); skills.deleteById(skillId); slas.deleteById(slaId);
    }
    FormResponse draft() {
        FormResponse form = service.createDraft(categoryId, null);
        return service.updateDraft(form.id(), new DraftRequest(form.revision(), false, 0, skillId, slaId, List.of()));
    }

    @Test void sameDraftPublishRejectsTheStaleConcurrentRequest() throws Exception {
        FormResponse f = draft();
        List<Object> outcomes = race(() -> service.publish(f.id(), f.revision(), null), () -> service.publish(f.id(), f.revision(), null));
        assertThat(outcomes.stream().filter(FormResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(StaleWriteException.class::isInstance)).hasSize(1);
        assertThat(service.published(categoryId).id()).isEqualTo(f.id());
    }

    @Test void differentDraftsSerializeTheirPublishTransitions() throws Exception {
        FormResponse a = draft(), b = draft();
        List<Object> outcomes = race(() -> service.publish(a.id(), a.revision(), null), () -> service.publish(b.id(), b.revision(), null));
        assertThat(outcomes).allMatch(FormResponse.class::isInstance);
        assertThat(forms.findByCategoryIdOrderByVersionNumberAsc(categoryId)).extracting(CategoryFormVersion::getFormStatus)
                .containsExactlyInAnyOrder(FormStatus.ARCHIVED, FormStatus.PUBLISHED);
    }

    @Test void concurrentDraftCreationGeneratesDistinctVersionNumbers() throws Exception {
        List<Object> outcomes = race(() -> service.createDraft(categoryId, null), () -> service.createDraft(categoryId, null));
        assertThat(outcomes).allMatch(FormResponse.class::isInstance);
        assertThat(forms.findByCategoryIdOrderByVersionNumberAsc(categoryId)).extracting(CategoryFormVersion::getVersionNumber).containsExactly(1, 2);
    }

    List<Object> race(Supplier<FormResponse> first, Supplier<FormResponse> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> a = pool.submit(() -> run(first, ready, start)), b = pool.submit(() -> run(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
    Object run(Supplier<FormResponse> action, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); start.await();
        try { return action.get(); } catch (RuntimeException e) { return e; }
    }
}
