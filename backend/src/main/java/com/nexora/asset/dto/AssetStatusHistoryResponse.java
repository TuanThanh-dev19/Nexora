package com.nexora.asset.dto;

import java.time.OffsetDateTime;

import com.nexora.asset.entity.AssetStatus;
import com.nexora.asset.entity.AssetStatusHistory;

public record AssetStatusHistoryResponse(
        Long id,
        Long assetId,
        AssetStatus fromStatus,
        AssetStatus toStatus,
        String reason,
        UserReference changedBy,
        Long ticketId,
        OffsetDateTime changedAt
) {
    public record UserReference(Long id, String fullName, String email) {}

    public static AssetStatusHistoryResponse from(AssetStatusHistory history) {
        UserReference userRef = history.getChangedBy() != null
                ? new UserReference(
                        history.getChangedBy().getId(),
                        history.getChangedBy().getFullName(),
                        history.getChangedBy().getEmail())
                : null;

        return new AssetStatusHistoryResponse(
                history.getId(),
                history.getAsset() != null ? history.getAsset().getId() : null,
                history.getFromStatus(),
                history.getToStatus(),
                history.getReason(),
                userRef,
                history.getTicketId(),
                history.getChangedAt()
        );
    }
}
