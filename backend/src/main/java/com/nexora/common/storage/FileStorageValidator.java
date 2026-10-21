package com.nexora.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Component
public class FileStorageValidator {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int MAX_FILE_COUNT = 5;
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private static final Map<String, byte[]> FILE_SIGNATURES =
            Map.of(
                    "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                    "image/png",
                            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.EMPTY, "File must not be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.TOO_LARGE,
                    "File exceeds the maximum size of 5 MiB.");
        }
        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.UNSUPPORTED_MEDIA,
                    "Only image/jpeg and image/png files are allowed.");
        }
    }

    public void validateCount(Collection<? extends MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.COUNT, "At least one file is required.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.COUNT, "No more than 5 files are allowed.");
        }
    }

    public void validateSignature(Path file, String contentType) {
        try (var input = Files.newInputStream(file)) {
            validateSignature(input, contentType);
        } catch (IOException exception) {
            throw new FileStorageException("Could not inspect uploaded file.", exception);
        }
    }

    /**
     * Reads only the signature; suitable for pre-persistence validation without temporary files.
     */
    public void validateSignature(MultipartFile file) {
        validate(file);
        try (var input = file.getInputStream()) {
            validateSignature(input, file.getContentType());
        } catch (IOException exception) {
            throw new FileStorageException("Could not inspect uploaded file.", exception);
        }
    }

    private void validateSignature(java.io.InputStream input, String contentType)
            throws IOException {
        byte[] expected = FILE_SIGNATURES.get(contentType);
        if (expected == null)
            throw new InvalidFileException(
                    InvalidFileException.Reason.UNSUPPORTED_MEDIA, "Unsupported image MIME.");
        byte[] actual = input.readNBytes(expected.length);
        if (actual.length < expected.length) {
            throw new InvalidFileException(
                    InvalidFileException.Reason.SIGNATURE,
                    "File content does not match its declared MIME type.");
        }
        for (int index = 0; index < expected.length; index++) {
            if (actual[index] != expected[index]) {
                throw new InvalidFileException(
                        InvalidFileException.Reason.SIGNATURE,
                        "File content does not match its declared MIME type.");
            }
        }
    }
}
