package com.nexora.ticket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.jpa.domain.Specification;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.EquipmentType;
import com.nexora.asset.repository.AssetRepository;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.category.entity.CategoryFormVersion;
import com.nexora.category.entity.IncidentCategory;
import com.nexora.category.repository.CategoryFormVersionRepository;
import com.nexora.category.repository.IncidentCategoryRepository;
import com.nexora.common.domain.Priority;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.workflow.TicketStatus;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.SkillRepository;

@NexoraJpaTest
class TicketRepositoryTest {

    private static final String TEST_PASSWORD_HASH = "test-only-password-hash";

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private IncidentCategoryRepository categories;

    @Autowired
    private CategoryFormVersionRepository formVersions;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private EquipmentTypeRepository equipmentTypes;

    @Autowired
    private AssetRepository assets;

    @Autowired
    private SkillRepository skills;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsCompleteTicketAndFindsItByIdAndTicketNumber() {
        References references = savedReferences("COMPLETE", true);
        Skill skill = skills.saveAndFlush(new Skill("TICKET-SKILL", "Ticket skill"));
        OffsetDateTime slaDueAt = OffsetDateTime.of(
                2026, 10, 5, 15, 30, 0, 123_456_000, ZoneOffset.ofHours(7));

        Ticket ticket = tickets.saveAndFlush(new Ticket(
                "NX-2026-000101",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                references.asset(),
                "Máy chiếu không hoạt động",
                "Thiết bị không khởi động sau khi cắm nguồn.",
                Priority.HIGH,
                slaDueAt,
                skill));
        Long ticketId = ticket.getId();
        entityManager.clear();

        Ticket loaded = tickets.findById(ticketId).orElseThrow();

        assertThat(loaded.getTicketNumber()).isEqualTo("NX-2026-000101");
        assertThat(loaded.getReporter().getId()).isEqualTo(references.reporter().getId());
        assertThat(loaded.getCategory().getId()).isEqualTo(references.category().getId());
        assertThat(loaded.getFormVersion().getId()).isEqualTo(references.formVersion().getId());
        assertThat(loaded.getLocation().getId()).isEqualTo(references.location().getId());
        assertThat(loaded.getAsset().getId()).isEqualTo(references.asset().getId());
        assertThat(loaded.getRequiredSkill().getId()).isEqualTo(skill.getId());
        assertThat(loaded.getTitle()).isEqualTo("Máy chiếu không hoạt động");
        assertThat(loaded.getDescription())
                .isEqualTo("Thiết bị không khởi động sau khi cắm nguồn.");
        assertThat(loaded.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(loaded.getSlaDueAt()).isEqualTo(slaDueAt);
        assertThat(loaded.getResolutionOutcome()).isNull();
        assertThat(loaded.getResolvedAt()).isNull();
        assertThat(loaded.getClosedAt()).isNull();
        assertThat(loaded.getDuplicateOf()).isNull();
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(tickets.findByTicketNumber("NX-2026-000101"))
                .get()
                .extracting(Ticket::getId)
                .isEqualTo(ticketId);
    }

    @Test
    void persistsTicketWithoutOptionalAsset() {
        References references = savedReferences("NO-ASSET", false);

        Ticket ticket = tickets.saveAndFlush(new Ticket(
                "NX-2026-000102",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Rò rỉ nước",
                "Có nước rò rỉ gần hành lang.",
                Priority.NORMAL));
        entityManager.clear();

        Ticket loaded = tickets.findByTicketNumber(ticket.getTicketNumber()).orElseThrow();

        assertThat(loaded.getAsset()).isNull();
        assertThat(loaded.getRequiredSkill()).isNull();
        assertThat(loaded.getSlaDueAt()).isNull();
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
    }

    @Test
    void rejectsDuplicateTicketNumber() {
        References references = savedReferences("DUPLICATE", false);
        tickets.saveAndFlush(newTicket("NX-2026-000103", references));

        assertThatThrownBy(() -> tickets.saveAndFlush(
                newTicket("NX-2026-000103", references)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingRequiredReferencesAndMismatchedCategoryForm() {
        References references = savedReferences("REQUIRED", false);
        IncidentCategory otherCategory = categories.saveAndFlush(
                new IncidentCategory("TICKET-OTHER", "Other category"));

        assertThatThrownBy(() -> new Ticket(
                "NX-2026-000104",
                null,
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Title",
                "Description",
                Priority.NORMAL))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reporter");

        assertThatThrownBy(() -> new Ticket(
                "NX-2026-000105",
                references.reporter(),
                otherCategory,
                references.formVersion(),
                references.location(),
                null,
                "Title",
                "Description",
                Priority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formVersion");
    }

    @Test
    void rejectsAStaleTicketUpdate() {
        References references = savedReferences("STALE", false);
        Ticket staleTicket = tickets.saveAndFlush(
                newTicket("NX-2026-000106", references));
        entityManager.detach(staleTicket);

        jdbcTemplate.update("""
                UPDATE dbo.tickets
                SET title = ?, version = version + 1
                WHERE id = ?
                """, "Updated concurrently", staleTicket.getId());

        assertThatThrownBy(() -> tickets.saveAndFlush(staleTicket))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void supportsDatabaseLevelVisibilitySpecifications() {
        References references = savedReferences("VISIBLE", false);
        AppUser anotherReporter = users.saveAndFlush(new AppUser(
                "another-ticket-reporter@example.test",
                TEST_PASSWORD_HASH,
                "Another Reporter"));
        tickets.saveAndFlush(newTicket("NX-2026-000107", references));
        tickets.saveAndFlush(new Ticket(
                "NX-2026-000108",
                anotherReporter,
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Other reporter ticket",
                "Should not be visible to the first reporter predicate.",
                Priority.LOW));
        entityManager.clear();

        Specification<Ticket> reportedBy = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("reporter").get("id"), references.reporter().getId());

        assertThat(tickets.findAll(reportedBy))
                .extracting(Ticket::getTicketNumber)
                .containsExactly("NX-2026-000107");
    }

    private Ticket newTicket(String ticketNumber, References references) {
        return new Ticket(
                ticketNumber,
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                references.asset(),
                "Ticket title",
                "Ticket description",
                Priority.NORMAL);
    }

    private References savedReferences(String suffix, boolean withAsset) {
        AppUser reporter = users.saveAndFlush(new AppUser(
                "ticket-" + suffix.toLowerCase() + "@example.test",
                TEST_PASSWORD_HASH,
                "Ticket Reporter"));
        IncidentCategory category = categories.saveAndFlush(new IncidentCategory(
                "TICKET-" + suffix,
                "Ticket category " + suffix));
        CategoryFormVersion formVersion = formVersions.saveAndFlush(
                new CategoryFormVersion(category, 1));
        Location location = locations.saveAndFlush(new Location(
                "TICKET-LOCATION-" + suffix,
                "Ticket location " + suffix,
                LocationType.ROOM,
                null));

        Asset asset = null;
        if (withAsset) {
            EquipmentType equipmentType = equipmentTypes.saveAndFlush(new EquipmentType(
                    "TICKET-EQUIPMENT-" + suffix,
                    "Ticket equipment " + suffix));
            asset = assets.saveAndFlush(new Asset(
                    "TICKET-ASSET-" + suffix,
                    equipmentType,
                    location));
        }
        return new References(reporter, category, formVersion, location, asset);
    }

    private record References(
            AppUser reporter,
            IncidentCategory category,
            CategoryFormVersion formVersion,
            Location location,
            Asset asset) {
    }
}
