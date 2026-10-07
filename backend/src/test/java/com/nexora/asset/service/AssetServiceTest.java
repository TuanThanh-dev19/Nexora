package com.nexora.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexora.asset.dto.AssetCreateRequest;
import com.nexora.asset.dto.AssetResponse;
import com.nexora.asset.dto.AssetStatusChangeRequest;
import com.nexora.asset.dto.AssetStatusHistoryResponse;
import com.nexora.asset.dto.AssetUpdateRequest;
import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;
import com.nexora.asset.entity.AssetStatusHistory;
import com.nexora.asset.entity.EquipmentType;
import com.nexora.asset.repository.AssetRepository;
import com.nexora.asset.repository.AssetStatusHistoryRepository;
import com.nexora.asset.repository.EquipmentTypeRepository;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.InvalidStateTransitionException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.exception.StaleWriteException;
import com.nexora.location.entity.Location;
import com.nexora.location.entity.LocationType;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.entity.AppUser;
import com.nexora.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetStatusHistoryRepository assetStatusHistoryRepository;

    @Mock
    private EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private AssetService assetService;

    private EquipmentType testEquipmentType;
    private Location testLocation;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(
                assetRepository,
                assetStatusHistoryRepository,
                equipmentTypeRepository,
                locationRepository,
                appUserRepository
        );

        testEquipmentType = new EquipmentType("EQ-PROJ", "Projector");
        ReflectionTestUtils.setField(testEquipmentType, "id", 10L);

        testLocation = new Location("LOC-A101", "Room A101", LocationType.ROOM, null);
        ReflectionTestUtils.setField(testLocation, "id", 20L);

        testUser = new AppUser("manager@campusfix.vn", "hashed", "Manager User");
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    @Nested
    @DisplayName("Create Asset")
    class CreateTests {

        @Test
        @DisplayName("Successfully creates asset and persists initial status history")
        void create_success_recordsInitialHistory() {
            AssetCreateRequest request = new AssetCreateRequest(
                    "AST-001",
                    10L,
                    20L,
                    "SN-12345",
                    "Conference projector",
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2028, 1, 1)
            );

            when(assetRepository.existsByAssetCode("AST-001")).thenReturn(false);
            when(assetRepository.existsBySerialNumber("SN-12345")).thenReturn(false);
            when(equipmentTypeRepository.findById(10L)).thenReturn(Optional.of(testEquipmentType));
            when(locationRepository.findById(20L)).thenReturn(Optional.of(testLocation));
            when(appUserRepository.findById(1L)).thenReturn(Optional.of(testUser));

            when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
                Asset asset = invocation.getArgument(0);
                ReflectionTestUtils.setField(asset, "id", 100L);
                ReflectionTestUtils.setField(asset, "version", 0L);
                return asset;
            });

            AssetResponse response = assetService.create(request, 1L);

            assertThat(response).isNotNull();
            assertThat(response.assetCode()).isEqualTo("AST-001");
            assertThat(response.status()).isEqualTo(AssetStatus.ACTIVE);
            assertThat(response.serialNumber()).isEqualTo("SN-12345");

            ArgumentCaptor<AssetStatusHistory> historyCaptor = ArgumentCaptor.forClass(AssetStatusHistory.class);
            verify(assetStatusHistoryRepository).save(historyCaptor.capture());
            AssetStatusHistory savedHistory = historyCaptor.getValue();
            assertThat(savedHistory.getFromStatus()).isNull();
            assertThat(savedHistory.getToStatus()).isEqualTo(AssetStatus.ACTIVE);
            assertThat(savedHistory.getReason()).isEqualTo("Initial asset registration");
            assertThat(savedHistory.getChangedBy()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Rejects create when asset code already exists")
        void create_duplicateAssetCode_throwsBusinessRuleException() {
            AssetCreateRequest request = new AssetCreateRequest(
                    "AST-DUPLICATE", 10L, 20L, null, null, null, null
            );
            when(assetRepository.existsByAssetCode("AST-DUPLICATE")).thenReturn(true);

            assertThatThrownBy(() -> assetService.create(request, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Asset code already exists: 'AST-DUPLICATE'");
        }

        @Test
        @DisplayName("Rejects create when serial number already exists")
        void create_duplicateSerialNumber_throwsBusinessRuleException() {
            AssetCreateRequest request = new AssetCreateRequest(
                    "AST-002", 10L, 20L, "SN-EXISTS", null, null, null
            );
            when(assetRepository.existsByAssetCode("AST-002")).thenReturn(false);
            when(assetRepository.existsBySerialNumber("SN-EXISTS")).thenReturn(true);

            assertThatThrownBy(() -> assetService.create(request, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Serial number already exists: 'SN-EXISTS'");
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when location does not exist")
        void create_locationNotFound_throwsResourceNotFoundException() {
            AssetCreateRequest request = new AssetCreateRequest(
                    "AST-003", 10L, 999L, null, null, null, null
            );
            when(assetRepository.existsByAssetCode("AST-003")).thenReturn(false);
            when(equipmentTypeRepository.findById(10L)).thenReturn(Optional.of(testEquipmentType));
            when(locationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> assetService.create(request, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Location not found: id=999");
        }
    }

    @Nested
    @DisplayName("Update Asset")
    class UpdateTests {

        @Test
        @DisplayName("Successfully updates asset attributes when version matches")
        void update_success() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 2L);

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));
            when(equipmentTypeRepository.findById(10L)).thenReturn(Optional.of(testEquipmentType));
            when(locationRepository.findById(20L)).thenReturn(Optional.of(testLocation));
            when(assetRepository.existsBySerialNumberAndIdNot("SN-NEW", 100L)).thenReturn(false);
            when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AssetUpdateRequest request = new AssetUpdateRequest(
                    2L, 10L, 20L, "SN-NEW", "Updated description",
                    LocalDate.of(2026, 2, 2), null
            );

            AssetResponse response = assetService.update(100L, request);

            assertThat(response).isNotNull();
            assertThat(response.description()).isEqualTo("Updated description");
            assertThat(response.serialNumber()).isEqualTo("SN-NEW");
        }

        @Test
        @DisplayName("Rejects update when version is stale (409 Conflict)")
        void update_staleVersion_throwsStaleWriteException() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 3L);

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));

            AssetUpdateRequest request = new AssetUpdateRequest(
                    2L, // Stale version
                    10L, 20L, null, null, null, null
            );

            assertThatThrownBy(() -> assetService.update(100L, request))
                    .isInstanceOf(StaleWriteException.class);
        }

        @Test
        @DisplayName("Rejects update when serial number is used by another asset")
        void update_duplicateSerialNumber_throwsBusinessRuleException() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 1L);

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));
            when(assetRepository.existsBySerialNumberAndIdNot("SN-TAKEN", 100L)).thenReturn(true);

            AssetUpdateRequest request = new AssetUpdateRequest(
                    1L, 10L, 20L, "SN-TAKEN", null, null, null
            );

            assertThatThrownBy(() -> assetService.update(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Serial number already exists: 'SN-TAKEN'");
        }
    }

    @Nested
    @DisplayName("Change Status")
    class ChangeStatusTests {

        @Test
        @DisplayName("Successfully changes status and appends history atomically")
        void changeStatus_success() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 2L);
            // Default status is ACTIVE

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));
            when(appUserRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                    AssetStatus.UNDER_MAINTENANCE,
                    "Thiết bị cần kiểm tra nguồn điện",
                    2L
            );

            AssetResponse response = assetService.changeStatus(100L, request, 1L);

            assertThat(response.status()).isEqualTo(AssetStatus.UNDER_MAINTENANCE);

            ArgumentCaptor<AssetStatusHistory> historyCaptor = ArgumentCaptor.forClass(AssetStatusHistory.class);
            verify(assetStatusHistoryRepository).save(historyCaptor.capture());
            AssetStatusHistory savedHistory = historyCaptor.getValue();
            assertThat(savedHistory.getFromStatus()).isEqualTo(AssetStatus.ACTIVE);
            assertThat(savedHistory.getToStatus()).isEqualTo(AssetStatus.UNDER_MAINTENANCE);
            assertThat(savedHistory.getReason()).isEqualTo("Thiết bị cần kiểm tra nguồn điện");
            assertThat(savedHistory.getChangedBy()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Rejects change to current status")
        void changeStatus_toSameStatus_throwsInvalidStateTransitionException() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 1L);

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));

            AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                    AssetStatus.ACTIVE, // Already ACTIVE
                    "Trying to set ACTIVE again",
                    1L
            );

            assertThatThrownBy(() -> assetService.changeStatus(100L, request, 1L))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("Asset is already in status ACTIVE");
        }

        @Test
        @DisplayName("Rejects status change when version is stale")
        void changeStatus_staleVersion_throwsStaleWriteException() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);
            ReflectionTestUtils.setField(asset, "version", 5L);

            when(assetRepository.findWithDetailsById(100L)).thenReturn(Optional.of(asset));

            AssetStatusChangeRequest request = new AssetStatusChangeRequest(
                    AssetStatus.RETIRED,
                    "End of life",
                    4L // Mismatch version
            );

            assertThatThrownBy(() -> assetService.changeStatus(100L, request, 1L))
                    .isInstanceOf(StaleWriteException.class);
        }
    }

    @Nested
    @DisplayName("Status History")
    class StatusHistoryTests {

        @Test
        @DisplayName("Returns history records ordered newest first")
        void getStatusHistory_success() {
            Asset asset = new Asset("AST-001", testEquipmentType, testLocation);
            ReflectionTestUtils.setField(asset, "id", 100L);

            when(assetRepository.existsById(100L)).thenReturn(true);

            AssetStatusHistory history1 = new AssetStatusHistory(
                    asset, AssetStatus.ACTIVE, AssetStatus.UNDER_MAINTENANCE, "Inspection", testUser
            );
            ReflectionTestUtils.setField(history1, "id", 2L);

            AssetStatusHistory history2 = new AssetStatusHistory(
                    asset, null, AssetStatus.ACTIVE, "Initial asset registration", testUser
            );
            ReflectionTestUtils.setField(history2, "id", 1L);

            when(assetStatusHistoryRepository.findByAssetIdOrderByChangedAtDesc(100L))
                    .thenReturn(List.of(history1, history2));

            List<AssetStatusHistoryResponse> responses = assetService.getStatusHistory(100L);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).toStatus()).isEqualTo(AssetStatus.UNDER_MAINTENANCE);
            assertThat(responses.get(1).toStatus()).isEqualTo(AssetStatus.ACTIVE);
        }

        @Test
        @DisplayName("Throws ResourceNotFoundException when asset does not exist")
        void getStatusHistory_assetNotFound_throwsResourceNotFoundException() {
            when(assetRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> assetService.getStatusHistory(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Asset not found: id=999");
        }
    }
}
