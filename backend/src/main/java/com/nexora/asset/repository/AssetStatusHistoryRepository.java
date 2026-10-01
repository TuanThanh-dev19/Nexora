package com.nexora.asset.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.asset.entity.AssetStatusHistory;

/**
 * Spring Data repository for {@link AssetStatusHistory}.
 *
 * <p>History records are immutable once persisted.  This repository should
 * be used for reads and for appending new entries written by a dedicated
 * transactional service.  Never update or delete history records.
 */
public interface AssetStatusHistoryRepository extends JpaRepository<AssetStatusHistory, Long> {

    /**
     * Returns all status-history entries for the given asset, newest first.
     *
     * <p>The ordering mirrors the {@code idx_asset_status_history_asset_time}
     * DB index ({@code asset_id, changed_at DESC}) to maximise query efficiency.
     *
     * @param assetId the PK of the asset
     * @return history entries ordered by {@code changed_at} descending; never {@code null}
     */
    @Query("""
            select h from AssetStatusHistory h
            where h.asset.id = :assetId
            order by h.changedAt desc
            """)
    List<AssetStatusHistory> findByAssetIdOrderByChangedAtDesc(@Param("assetId") Long assetId);
}
