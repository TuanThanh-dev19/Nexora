package com.nexora.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class FileStorageTransactionCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageTransactionCleanup.class);

    private final FileStorageService fileStorageService;

    public FileStorageTransactionCleanup(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void deleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An active transaction synchronization is required.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    fileStorageService.delete(storageKey);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not clean up stored file after transaction rollback: {}", storageKey, exception);
                }
            }
        });
    }
}
