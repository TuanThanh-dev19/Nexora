package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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
import com.nexora.ticket.entity.ResolutionOutcome;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.workflow.TicketStatus;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.Skill;
import com.nexora.user.repository.AppUserRepository;
import com.nexora.user.repository.SkillRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TicketPersistenceSqlServerIntegrationTest {

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
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsCompleteAndAssetOptionalTicketsAgainstFlywaySchema() {
        References references = savedReferences("SQL-MAP", true);
        Skill requiredSkill = skills.saveAndFlush(new Skill(
                "SQL-TICKET-SKILL",
                "Kỹ năng xử lý ticket"));
        OffsetDateTime slaDueAt = OffsetDateTime.of(
                2026, 10, 5, 15, 30, 0, 123_456_700, ZoneOffset.ofHours(7));

        Ticket withAsset = tickets.saveAndFlush(new Ticket(
                "NX-2026-000201",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                references.asset(),
                "Máy chiếu không hoạt động",
                "Mô tả Unicode được lưu trong NVARCHAR(MAX).",
                Priority.URGENT,
                slaDueAt,
                requiredSkill));
        Ticket withoutAsset = tickets.saveAndFlush(new Ticket(
                "NX-2026-000202",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Ticket không có tài sản",
                "Asset là quan hệ tùy chọn.",
                Priority.NORMAL));
        entityManager.clear();

        Ticket loadedWithAsset = tickets.findByTicketNumber("NX-2026-000201").orElseThrow();
        assertThat(loadedWithAsset.getId()).isEqualTo(withAsset.getId());
        assertThat(loadedWithAsset.getReporter().getId()).isEqualTo(references.reporter().getId());
        assertThat(loadedWithAsset.getCategory().getId()).isEqualTo(references.category().getId());
        assertThat(loadedWithAsset.getFormVersion().getId())
                .isEqualTo(references.formVersion().getId());
        assertThat(loadedWithAsset.getLocation().getId()).isEqualTo(references.location().getId());
        assertThat(loadedWithAsset.getAsset().getId()).isEqualTo(references.asset().getId());
        assertThat(loadedWithAsset.getRequiredSkill().getId()).isEqualTo(requiredSkill.getId());
        assertThat(loadedWithAsset.getTitle()).isEqualTo("Máy chiếu không hoạt động");
        assertThat(loadedWithAsset.getDescription())
                .isEqualTo("Mô tả Unicode được lưu trong NVARCHAR(MAX).");
        assertThat(loadedWithAsset.getPriority()).isEqualTo(Priority.URGENT);
        assertThat(loadedWithAsset.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(loadedWithAsset.getSlaDueAt()).isEqualTo(slaDueAt);
        assertThat(loadedWithAsset.getVersion()).isZero();

        Ticket loadedWithoutAsset = tickets.findById(withoutAsset.getId()).orElseThrow();
        assertThat(loadedWithoutAsset.getAsset()).isNull();
    }

    @Test
    void readsHistoricalLifecycleColumnsWithoutImplementingWorkflowWrites() {
        References references = savedReferences("SQL-HISTORY", false);
        OffsetDateTime resolvedAt = OffsetDateTime.of(
                2026, 10, 3, 8, 15, 0, 123_456_700, ZoneOffset.ofHours(7));
        OffsetDateTime closedAt = resolvedAt.plusHours(2);

        jdbcTemplate.update("""
                INSERT INTO dbo.tickets (
                    ticket_number, reporter_id, category_id, form_version_id, location_id,
                    title, description, priority, ticket_status, resolution_outcome,
                    resolved_at, closed_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "NX-2026-000203",
                references.reporter().getId(),
                references.category().getId(),
                references.formVersion().getId(),
                references.location().getId(),
                "Historical closed ticket",
                "Inserted as a valid historical row for mapping verification.",
                Priority.HIGH.name(),
                TicketStatus.CLOSED.name(),
                ResolutionOutcome.FIXED.name(),
                resolvedAt,
                closedAt,
                4L,
                resolvedAt.minusDays(1),
                closedAt);
        entityManager.clear();

        Ticket loaded = tickets.findByTicketNumber("NX-2026-000203").orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(loaded.getResolutionOutcome()).isEqualTo(ResolutionOutcome.FIXED);
        assertThat(loaded.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(loaded.getClosedAt()).isEqualTo(closedAt);
        assertThat(loaded.getVersion()).isEqualTo(4L);
    }

    @Test
    void enforcesTicketNumberAndCategoryFormConstraintsInSqlServer() {
        References references = savedReferences("SQL-CONSTRAINT", false);
        tickets.saveAndFlush(new Ticket(
                "NX-2026-000204",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Unique ticket",
                "First row",
                Priority.NORMAL));

        assertThatThrownBy(() -> tickets.saveAndFlush(new Ticket(
                "NX-2026-000204",
                references.reporter(),
                references.category(),
                references.formVersion(),
                references.location(),
                null,
                "Duplicate ticket",
                "Second row",
                Priority.NORMAL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMismatchedCategoryAndFormAtTheSqlServerBoundary() {
        References references = savedReferences("SQL-PAIR-A", false);
        IncidentCategory otherCategory = categories.saveAndFlush(new IncidentCategory(
                "SQL-PAIR-B",
                "SQL pair category B"));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO dbo.tickets (
                    ticket_number, reporter_id, category_id, form_version_id, location_id,
                    title, description, priority, ticket_status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "NX-2026-000205",
                references.reporter().getId(),
                otherCategory.getId(),
                references.formVersion().getId(),
                references.location().getId(),
                "Mismatched category/form",
                "Must be rejected by fk_tickets_category_form.",
                Priority.NORMAL.name(),
                TicketStatus.SUBMITTED.name(),
                0L,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private References savedReferences(String suffix, boolean withAsset) {
        AppUser reporter = users.saveAndFlush(new AppUser(
                "ticket-" + suffix.toLowerCase() + "@example.test",
                TEST_PASSWORD_HASH,
                "SQL Ticket Reporter"));
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
