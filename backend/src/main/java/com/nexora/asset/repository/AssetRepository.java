package com.nexora.asset.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.asset.entity.Asset;
import com.nexora.asset.entity.AssetStatus;

/**
 * Spring Data repository for {@link Asset}.
 *
 * <p>All status-changing operations must go through a dedicated transactional
 * service — do not use {@link #save} to change {@code asset_status} directly.
 */
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    /**
     * Looks up an asset by its unique business identifier.
     *
     * @param assetCode the unique asset code (e.g. "PROJ-A101-001")
     * @return the asset wrapped in an {@link Optional}, or empty if not found
     */
    Optional<Asset> findByAssetCode(String assetCode);

    boolean existsByAssetCode(String assetCode);

    boolean existsByAssetCodeAndIdNot(String assetCode, Long id);

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, Long id);

    @Override
    @EntityGraph(attributePaths = {"equipmentType", "location"})
    Page<Asset> findAll(Specification<Asset> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"equipmentType", "location"})
    @Query("select a from Asset a where a.id = :id")
    Optional<Asset> findWithDetailsById(@Param("id") Long id);

    /**
     * Returns all assets located at the given location.
     *
     * @param locationId the PK of the location
     * @return assets at that location, unordered
     */
    List<Asset> findByLocationId(Long locationId);

    /**
     * Returns all assets that currently have the given status.
     *
     * @param status the lifecycle state to filter by
     * @return matching assets, unordered
     */
    List<Asset> findByStatus(AssetStatus status);

    /**
     * Returns all assets at the given location that currently have the given status.
     * Mirrors the {@code idx_assets_location_status} DB index.
     *
     * @param locationId the PK of the location
     * @param status     the lifecycle state to filter by
     * @return matching assets, unordered
     */
    List<Asset> findByLocationIdAndStatus(Long locationId, AssetStatus status);

    /**
     * Returns all assets of a given equipment type.
     * Mirrors the {@code idx_assets_equipment_type_id} DB index.
     *
     * @param equipmentTypeId the PK of the equipment type
     * @return matching assets, unordered
     */
    List<Asset> findByEquipmentTypeId(Long equipmentTypeId);
}
