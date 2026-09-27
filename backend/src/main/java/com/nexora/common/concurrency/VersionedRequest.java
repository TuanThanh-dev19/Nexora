package com.nexora.common.concurrency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Contract for update/delete request DTOs that use optimistic concurrency.
 * Implementations expose the entity version observed by the client.
 */
public interface VersionedRequest {

    @NotNull
    @PositiveOrZero
    Long version();
}
