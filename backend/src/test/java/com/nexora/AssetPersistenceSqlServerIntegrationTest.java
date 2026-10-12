package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;
import com.nexora.asset.entity.AssetStatusHistory;
import com.nexora.asset.entity.EquipmentType;
import com.nexora.asset.repository.AssetRepository;
import com.nexora.asset.repository.AssetStatusHistoryRepository;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.entity.AppUser;
import com.nexora.user.repository.AppUserRepository;

import jakarta.persistence.EntityManager;

/**
 * Integration tests for {@link Asset} and {@link AssetStatusHistory} persistence.
 *
 * <p>These tests require a live SQL Server instance provided by Testcontainers.
 * They are gated by the {@code RUN_SQLSERVER_IT=true} environment variable to
 * keep the default CI build fast.
 */
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class AssetPersistenceSqlServerIntegrationTest {

    private static final String TEST_PASSWORD_HASH = "test-only-password-hash";

    @Autowired
    private AssetRepository assets;

    @Autowired
    private AssetStatusHistoryRepository historyRepository;

    @Autowired
    private EquipmentTypeRepository equipmentTypes;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private EntityManager entityManager;

    // =========================================================================
    // Helper factories
    // =========================================================================

    private Location savedLocation(String code, String name) {
        return locations.saveAndFlush(new Location(code, name, LocationType.ROOM, null));
    }

    private EquipmentType savedEquipmentType(String code, String name) {
        return equipmentTypes.saveAndFlush(new EquipmentType(code, name));
    }

    private AppUser savedUser(String email) {
        return users.saveAndFlush(new AppUser(email, TEST_PASSWORD_HASH, "Test User"));
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    void persistsAssetWithEquipmentTypeAndLocationRelationships() {
        Location room = savedLocation("AST-ROOM-01", "Phòng kiểm thử 01");
        EquipmentType projector = savedEquipmentType("AST-PROJ", "Máy chiếu");

        Asset asset = new Asset("PROJ-AST-001", projector, room);
        asset.changeDescription("Máy chiếu phòng họp A");
        assets.saveAndFlush(asset);
        entityManager.clear();

        Asset loaded = assets.findByAssetCode("PROJ-AST-001").orElseThrow();
        assertThat(loaded.getAssetCode()).isEqualTo("PROJ-AST-001");
        assertThat(loaded.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(loaded.getDescription()).isEqualTo("Máy chiếu phòng họp A");
        assertThat(loaded.getEquipmentType().getId()).isEqualTo(projector.getId());
        assertThat(loaded.getLocation().getId()).isEqualTo(room.getId());
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void uniqueAssetCodeIsEnforced() {
        Location room = savedLocation("AST-ROOM-02", "Phòng kiểm thử 02");
        EquipmentType monitor = savedEquipmentType("AST-MON", "Màn hình");

        assets.saveAndFlush(new Asset("DUPLICATE-CODE", monitor, room));

        assertThatThrownBy(() ->
                assets.saveAndFlush(new Asset("DUPLICATE-CODE", monitor, room)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void warrantyUntilAndPurchasedAtAreMappedCorrectly() {
        Location room = savedLocation("AST-ROOM-03", "Phòng kiểm thử 03");
        EquipmentType printer = savedEquipmentType("AST-PRT", "Máy in");

        LocalDate purchased = LocalDate.of(2024, 3, 15);
        LocalDate warranty = LocalDate.of(2027, 3, 14);

        Asset asset = new Asset("PRT-AST-001", printer, room);
        asset.changePurchasedAt(purchased);
        asset.changeWarrantyUntil(warranty);
        assets.saveAndFlush(asset);
        entityManager.clear();

        Asset loaded = assets.findByAssetCode("PRT-AST-001").orElseThrow();
        assertThat(loaded.getPurchasedAt()).isEqualTo(purchased);
        assertThat(loaded.getWarrantyUntil()).isEqualTo(warranty);
    }

    @Test
    void optimisticLockingIncrementsVersionOnUpdate() {
        Location room = savedLocation("AST-ROOM-04", "Phòng kiểm thử 04");
        EquipmentType screen = savedEquipmentType("AST-SCR", "Màn hình chiếu");

        Asset asset = assets.saveAndFlush(new Asset("SCR-AST-001", screen, room));
        Long initialVersion = asset.getVersion();

        asset.changeDescription("Mô tả đã cập nhật");
        assets.flush();

        assertThat(asset.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void statusHistoryIsReturnedNewestFirst() {
        Location room = savedLocation("AST-ROOM-05", "Phòng kiểm thử 05");
        EquipmentType ac = savedEquipmentType("AST-AC", "Điều hòa");
        AppUser actor = savedUser("asset-actor@example.test");

        Asset asset = assets.saveAndFlush(new Asset("AC-AST-001", ac, room));

        OffsetDateTime firstChange = OffsetDateTime.of(2026, 1, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime secondChange = OffsetDateTime.of(2026, 3, 20, 9, 0, 0, 0, ZoneOffset.UTC);

        // Older record
        AssetStatusHistory firstEntry = new AssetStatusHistory(
                asset, AssetStatus.ACTIVE, AssetStatus.UNDER_MAINTENANCE,
                "Bảo trì định kỳ", actor);
        // Set changedAt via field reflection would bypass @PrePersist; use JPQL update instead.
        historyRepository.saveAndFlush(firstEntry);

        // Newer record
        AssetStatusHistory secondEntry = new AssetStatusHistory(
                asset, AssetStatus.UNDER_MAINTENANCE, AssetStatus.ACTIVE,
                "Hoàn tất bảo trì", actor);
        historyRepository.saveAndFlush(secondEntry);
        entityManager.clear();

        // Force ordering via changed_at by updating the first entry's timestamp directly
        entityManager.createNativeQuery("""
                UPDATE dbo.asset_status_history
                SET changed_at = :ts
                WHERE id = :id
                """)
                .setParameter("ts", firstChange)
                .setParameter("id", firstEntry.getId())
                .executeUpdate();

        entityManager.createNativeQuery("""
                UPDATE dbo.asset_status_history
                SET changed_at = :ts
                WHERE id = :id
                """)
                .setParameter("ts", secondChange)
                .setParameter("id", secondEntry.getId())
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();

        List<AssetStatusHistory> history =
                historyRepository.findByAssetIdOrderByChangedAtDesc(asset.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getToStatus()).isEqualTo(AssetStatus.ACTIVE);          // newer
        assertThat(history.get(1).getToStatus()).isEqualTo(AssetStatus.UNDER_MAINTENANCE); // older
    }

    @Test
    void statusHistoryPreservesAllFields() {
        Location room = savedLocation("AST-ROOM-06", "Phòng kiểm thử 06");
        EquipmentType fan = savedEquipmentType("AST-FAN", "Quạt công nghiệp");
        AppUser actor = savedUser("history-actor@example.test");

        Asset asset = assets.saveAndFlush(new Asset("FAN-AST-001", fan, room));

        // ticket_id is nullable — the FK to dbo.tickets cannot be tested here
        // without a persisted Ticket (Ticket entity is out of scope for this issue).
        // We verify the field maps correctly as null; the non-null path will be
        // covered by the ticket persistence integration tests.
        AssetStatusHistory history = new AssetStatusHistory(
                asset,
                AssetStatus.ACTIVE,
                AssetStatus.OUT_OF_SERVICE,
                "Hỏng hóc nghiêm trọng",
                actor);
        historyRepository.saveAndFlush(history);
        entityManager.clear();

        List<AssetStatusHistory> loaded =
                historyRepository.findByAssetIdOrderByChangedAtDesc(asset.getId());

        assertThat(loaded).hasSize(1);
        AssetStatusHistory record = loaded.get(0);
        assertThat(record.getFromStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(record.getToStatus()).isEqualTo(AssetStatus.OUT_OF_SERVICE);
        assertThat(record.getReason()).isEqualTo("Hỏng hóc nghiêm trọng");
        assertThat(record.getChangedBy().getId()).isEqualTo(actor.getId());
        assertThat(record.getTicketId()).isNull();
        assertThat(record.getChangedAt()).isNotNull();
    }

    @Test
    void assetQueriesByLocationEquipmentTypeAndStatus() {
        Location room = savedLocation("AST-ROOM-07", "Phòng kiểm thử 07");
        Location otherRoom = savedLocation("AST-ROOM-08", "Phòng kiểm thử 08");
        EquipmentType laptop = savedEquipmentType("AST-LAP", "Laptop");
        EquipmentType desk = savedEquipmentType("AST-DSK", "Bàn làm việc");

        Asset a1 = assets.saveAndFlush(new Asset("LAP-AST-001", laptop, room));
        Asset a2 = assets.saveAndFlush(new Asset("LAP-AST-002", laptop, otherRoom));
        Asset a3 = assets.saveAndFlush(new Asset("DSK-AST-001", desk, room));
        entityManager.clear();

        // findByLocationId
        List<Asset> inRoom = assets.findByLocationId(room.getId());
        assertThat(inRoom)
                .extracting(Asset::getId)
                .containsExactlyInAnyOrder(a1.getId(), a3.getId());

        // findByEquipmentTypeId
        List<Asset> laptops = assets.findByEquipmentTypeId(laptop.getId());
        assertThat(laptops)
                .extracting(Asset::getId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId());

        // findByStatus (all ACTIVE by default)
        List<Asset> activeAssets = assets.findByStatus(AssetStatus.ACTIVE);
        assertThat(activeAssets)
                .extracting(Asset::getId)
                .contains(a1.getId(), a2.getId(), a3.getId());

        // findByLocationIdAndStatus
        List<Asset> activeInRoom = assets.findByLocationIdAndStatus(room.getId(), AssetStatus.ACTIVE);
        assertThat(activeInRoom)
                .extracting(Asset::getId)
                .containsExactlyInAnyOrder(a1.getId(), a3.getId());
    }
}
