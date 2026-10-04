package com.nexora.common.storage;

public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(String storageKey) {
        super("Stored file was not found for key: " + storageKey);
    }
}
