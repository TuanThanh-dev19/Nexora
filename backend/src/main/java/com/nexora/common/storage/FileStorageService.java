package com.nexora.common.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file);

    Resource open(String storageKey);

    void delete(String storageKey);
}
