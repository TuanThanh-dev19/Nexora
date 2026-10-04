package com.nexora.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileStorageValidator {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_FILE_COUNT = 5;
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private static final Map<String, byte[]> FILE_SIGNATURES = Map.of(
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File must not be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File exceeds the maximum size of 5 MiB.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException("Only image/jpeg and image/png files are allowed.");
        }
    }

    public void validateCount(Collection<? extends MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidFileException("At least one file is required.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new InvalidFileException("No more than 5 files are allowed.");
        }
    }

    public void validateSignature(Path file, String contentType) {
        byte[] expected = FILE_SIGNATURES.get(contentType);
        try {
            byte[] actual = Files.readAllBytes(file);
            if (actual.length < expected.length) {
                throw new InvalidFileException("File content does not match its declared MIME type.");
            }
            for (int index = 0; index < expected.length; index++) {
                if (actual[index] != expected[index]) {
                    throw new InvalidFileException("File content does not match its declared MIME type.");
                }
            }
        } catch (IOException exception) {
            throw new FileStorageException("Could not inspect uploaded file.", exception);
        }
    }
}
