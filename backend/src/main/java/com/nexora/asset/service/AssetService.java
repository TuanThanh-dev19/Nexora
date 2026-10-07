package com.nexora.asset.service;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.asset.dto.AssetCreateRequest;
import com.nexora.asset.dto.AssetListQuery;
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
import com.nexora.common.pagination.PageResponse;
import com.nexora.location.entity.Location;
import com.nexora.location.repository.LocationRepository;
import com.nexora.user.entity.AppUser;
import com.nexora.user.repository.AppUserRepository;

/**
 * Business service for Asset lifecycle management and status tracking.
 *
 * <p>Enforces the following rules:
 * <ul>
 *   <li>Unique assetCode across all assets.</li>
 *   <li>Unique serialNumber when provided.</li>
 *   <li>Location and EquipmentType must exist.</li>
 *   <li>Optimistic concurrency checks on update and status transitions.</li>
 *   <li>Atomic status change with {@link AssetStatusHistory} creation in the same transaction.</li>
 *   <li>Disallows transitioning to the current status.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetStatusHistoryRepository assetStatusHistoryRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final LocationRepository locationRepository;
    private final AppUserRepository appUserRepository;

    public AssetService(
            AssetRepository assetRepository,
            AssetStatusHistoryRepository assetStatusHistoryRepository,
            EquipmentTypeRepository equipmentTypeRepository,
            LocationRepository locationRepository,
            AppUserRepository appUserRepository) {
        this.assetRepository = assetRepository;
        this.assetStatusHistoryRepository = assetStatusHistoryRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.locationRepository = locationRepository;
        this.appUserRepository = appUserRepository;
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Lists assets according to the provided validated query filters and pagination.
     */
    public PageResponse<AssetResponse> list(AssetListQuery query) {
        Page<Asset> page = assetRepository.findAll(query.toSpecification(), query.pageable());
        return PageResponse.from(page.map(AssetResponse::from));
    }

    /**
     * Fetches details of a single asset by its primary key.
     *
     * @throws ResourceNotFoundException if the asset does not exist
     */
    public AssetResponse getById(Long id) {
        return AssetResponse.from(findOrThrow(id));
    }

    /**
     * Retrieves the audit history of status transitions for an asset, ordered newest first.
     *
     * @throws ResourceNotFoundException if the asset does not exist
     */
    public List<AssetStatusHistoryResponse> getStatusHistory(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset not found: id=" + assetId);
        }
        return assetStatusHistoryRepository.findByAssetIdOrderByChangedAtDesc(assetId)
                .stream()
                .map(AssetStatusHistoryResponse::from)
                .toList();
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Registers a new asset with initial status {@link AssetStatus#ACTIVE}
     * and records an initial {@link AssetStatusHistory} entry.
     *
     * @throws BusinessRuleException     if assetCode or serialNumber already exists
     * @throws ResourceNotFoundException if location or equipmentType is not found
     */
    @Transactional
    public AssetResponse create(AssetCreateRequest request, Long currentUserId) {
        validateUniqueAssetCodeForCreate(request.assetCode());
        validateUniqueSerialNumberForCreate(request.serialNumber());

        EquipmentType equipmentType = findEquipmentTypeOrThrow(request.equipmentTypeId());
        Location location = findLocationOrThrow(request.locationId());
        AppUser currentUser = resolveUser(currentUserId);

        Asset asset = new Asset(request.assetCode(), equipmentType, location);
        if (request.serialNumber() != null) {
            asset.changeSerialNumber(request.serialNumber());
        }
        if (request.description() != null) {
            asset.changeDescription(request.description());
        }
        if (request.purchasedAt() != null) {
            asset.changePurchasedAt(request.purchasedAt());
        }
        if (request.warrantyUntil() != null) {
            asset.changeWarrantyUntil(request.warrantyUntil());
        }

        Asset savedAsset = assetRepository.save(asset);

        AssetStatusHistory initialHistory = new AssetStatusHistory(
                savedAsset,
                null,
                AssetStatus.ACTIVE,
                "Initial asset registration",
                currentUser
        );
        assetStatusHistoryRepository.save(initialHistory);

        return AssetResponse.from(savedAsset);
    }

    /**
     * Updates an asset's editable attributes (excluding status).
     *
     * @throws ResourceNotFoundException if asset, location, or equipmentType is not found
     * @throws StaleWriteException       if version does not match
     * @throws BusinessRuleException     if serialNumber is already in use by another asset
     */
    @Transactional
    public AssetResponse update(Long id, AssetUpdateRequest request) {
        Asset asset = findOrThrow(id);
        validateVersion(asset, request.version());

        validateUniqueSerialNumberForUpdate(request.serialNumber(), id);

        EquipmentType equipmentType = findEquipmentTypeOrThrow(request.equipmentTypeId());
        Location location = findLocationOrThrow(request.locationId());

        asset.changeEquipmentType(equipmentType);
        asset.changeLocation(location);
        asset.changeSerialNumber(request.serialNumber());
        asset.changeDescription(request.description());
        asset.changePurchasedAt(request.purchasedAt());
        asset.changeWarrantyUntil(request.warrantyUntil());

        return AssetResponse.from(assetRepository.save(asset));
    }

    /**
     * Performs a state transition for an asset and records history in the same transaction.
     *
     * @throws ResourceNotFoundException       if the asset is not found
     * @throws StaleWriteException             if version does not match
     * @throws InvalidStateTransitionException if attempting to transition to the current status
     */
    @Transactional
    public AssetResponse changeStatus(Long id, AssetStatusChangeRequest request, Long currentUserId) {
        Asset asset = findOrThrow(id);
        validateVersion(asset, request.version());

        if (asset.getStatus() == request.targetStatus()) {
            throw new InvalidStateTransitionException(
                    "Asset is already in status " + request.targetStatus());
        }

        AppUser currentUser = resolveUser(currentUserId);
        AssetStatus fromStatus = asset.getStatus();

        asset.changeStatus(request.targetStatus());
        Asset savedAsset = assetRepository.save(asset);

        AssetStatusHistory history = new AssetStatusHistory(
                savedAsset,
                fromStatus,
                request.targetStatus(),
                request.reason(),
                currentUser
        );
        assetStatusHistoryRepository.save(history);

        return AssetResponse.from(savedAsset);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Asset findOrThrow(Long id) {
        return assetRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: id=" + id));
    }

    private EquipmentType findEquipmentTypeOrThrow(Long id) {
        return equipmentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EquipmentType not found: id=" + id));
    }

    private Location findLocationOrThrow(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: id=" + id));
    }

    private AppUser resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return appUserRepository.findById(userId).orElse(null);
    }

    private void validateVersion(Asset asset, Long requestVersion) {
        if (!Objects.equals(asset.getVersion(), requestVersion)) {
            throw new StaleWriteException();
        }
    }

    private void validateUniqueAssetCodeForCreate(String assetCode) {
        if (assetRepository.existsByAssetCode(assetCode)) {
            throw new BusinessRuleException("Asset code already exists: '" + assetCode + "'");
        }
    }

    private void validateUniqueSerialNumberForCreate(String serialNumber) {
        if (serialNumber != null && assetRepository.existsBySerialNumber(serialNumber)) {
            throw new BusinessRuleException("Serial number already exists: '" + serialNumber + "'");
        }
    }

    private void validateUniqueSerialNumberForUpdate(String serialNumber, Long assetId) {
        if (serialNumber != null && assetRepository.existsBySerialNumberAndIdNot(serialNumber, assetId)) {
            throw new BusinessRuleException("Serial number already exists: '" + serialNumber + "'");
        }
    }
}
