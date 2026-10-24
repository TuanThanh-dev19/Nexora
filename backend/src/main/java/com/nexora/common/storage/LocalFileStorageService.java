package com.nexora.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nexora.config.FileStorageProperties;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private final Path storageRoot;
    private final FileStorageValidator validator;

    public LocalFileStorageService(FileStorageProperties properties, FileStorageValidator validator) {
        this.storageRoot = properties.getRoot().toAbsolutePath().normalize();
        this.validator = validator;
        createStorageRoot();
    }

    @Override
    public StoredFile store(MultipartFile file) {
        validator.validate(file);

        String storageKey = UUID.randomUUID().toString();
        Path target = resolveStorageKey(storageKey);
        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(storageRoot, ".upload-", ".tmp");
            try (InputStream input = file.getInputStream();
                    OutputStream output = Files.newOutputStream(temporaryFile)) {
                copyWithLimit(input, output);
            }
            validator.validateSignature(temporaryFile, file.getContentType());
            long storedSize = Files.size(temporaryFile);
            moveIntoPlace(temporaryFile, target);
            temporaryFile = null;
            return new StoredFile(
                    storageKey,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    storedSize);
        } catch (InvalidFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileStorageException("Could not store uploaded file.", exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public Resource open(String storageKey) {
        Path storedFile = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(storedFile)) {
            throw new StoredFileNotFoundException(storageKey);
        }
        try {
            return new UrlResource(storedFile.toUri());
        } catch (IOException exception) {
            throw new FileStorageException("Could not open stored file.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path storedFile = resolveStorageKey(storageKey);
        try {
            Files.deleteIfExists(storedFile);
        } catch (IOException exception) {
            throw new FileStorageException("Could not delete stored file.", exception);
        }
    }

    private void createStorageRoot() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new FileStorageException("Could not create file storage directory.", exception);
        }
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new InvalidFileException("Invalid storage key.");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new InvalidFileException("Invalid storage key.");
        }
        return resolved;
    }

    private static void copyWithLimit(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            total += bytesRead;
            if (total > FileStorageValidator.MAX_FILE_SIZE) {
                throw new InvalidFileException(InvalidFileException.Reason.TOO_LARGE,
                        "File exceeds the maximum size of 5 MiB.");
            }
            output.write(buffer, 0, bytesRead);
        }
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // Best-effort cleanup; the original storage failure is more actionable.
        }
    }
}
