package com.nexora.common.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FileStorageTransactionCleanupTest {

    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileStorageTransactionCleanup cleanup = new FileStorageTransactionCleanup(fileStorageService);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesStoredFileAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup.deleteOnRollback("123e4567-e89b-42d3-a456-426614174000");

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorageService).delete("123e4567-e89b-42d3-a456-426614174000");
    }

    @Test
    void keepsStoredFileAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        cleanup.deleteOnRollback("123e4567-e89b-42d3-a456-426614174000");

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(fileStorageService, never()).delete("123e4567-e89b-42d3-a456-426614174000");
    }

    @Test
    void requiresAnActiveTransactionSynchronization() {
        assertThatThrownBy(() -> cleanup.deleteOnRollback("123e4567-e89b-42d3-a456-426614174000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction synchronization");
    }
}
