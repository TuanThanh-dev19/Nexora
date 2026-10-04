package com.nexora.common.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long size) {
}
