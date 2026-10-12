package com.nexora.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

import com.nexora.config.FileStorageProperties;

class LocalFileStorageServiceTest {

    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02
    };
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };

    @TempDir
    Path storageRoot;

    @ParameterizedTest
    @MethodSource("validImages")
    void storesOpensAndDeletesAllowedImages(String contentType, byte[] content) throws IOException {
        LocalFileStorageService service = service();
        MockMultipartFile upload = new MockMultipartFile("file", "evidence", contentType, content);

        StoredFile stored = service.store(upload);

        assertThat(stored.storageKey()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(stored.originalFilename()).isEqualTo("evidence");
        assertThat(stored.contentType()).isEqualTo(contentType);
        assertThat(stored.size()).isEqualTo(content.length);
        try (InputStream input = service.open(stored.storageKey()).getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }

        service.delete(stored.storageKey());

        assertThatThrownBy(() -> service.open(stored.storageKey()))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void rejectsUnsupportedMimeTypeAndLeavesNoTemporaryFile() throws IOException {
        LocalFileStorageService service = service();
        MockMultipartFile upload = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.store(upload))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("image/jpeg");
        assertThat(Files.list(storageRoot)).isEmpty();
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredMimeTypeAndCleansTemporaryFile() throws IOException {
        LocalFileStorageService service = service();
        MockMultipartFile upload = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-a-png".getBytes());

        assertThatThrownBy(() -> service.store(upload))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("does not match");
        assertThat(Files.list(storageRoot)).isEmpty();
    }

    @Test
    void rejectsFileLargerThanFiveMebibytes() throws IOException {
        LocalFileStorageService service = service();
        byte[] oversized = new byte[(int) FileStorageValidator.MAX_FILE_SIZE + 1];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;
        MockMultipartFile upload = new MockMultipartFile("file", "large.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> service.store(upload))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("5 MiB");
        assertThat(Files.list(storageRoot)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("untrustedFilenames")
    void neverUsesOriginalFilenameAsAPath(String originalFilename) {
        LocalFileStorageService service = service();
        MockMultipartFile upload = new MockMultipartFile("file", originalFilename, "image/png", PNG);

        StoredFile stored = service.store(upload);

        assertThat(stored.originalFilename()).isEqualTo(originalFilename);
        assertThat(storageRoot.resolve(stored.storageKey())).isRegularFile();
        assertThat(storageRoot.resolve("outside.png")).doesNotExist();
    }

    @Test
    void duplicateOriginalFilenamesNeverOverwriteEachOther() throws IOException {
        LocalFileStorageService service = service();
        MockMultipartFile first = new MockMultipartFile("file", "same.png", "image/png", PNG);
        byte[] otherPng = PNG.clone();
        otherPng[otherPng.length - 1] = 0x02;
        MockMultipartFile second = new MockMultipartFile("file", "same.png", "image/png", otherPng);

        StoredFile firstStored = service.store(first);
        StoredFile secondStored = service.store(second);

        assertThat(firstStored.storageKey()).isNotEqualTo(secondStored.storageKey());
        try (InputStream firstInput = service.open(firstStored.storageKey()).getInputStream();
                InputStream secondInput = service.open(secondStored.storageKey()).getInputStream()) {
            assertThat(firstInput.readAllBytes()).isEqualTo(PNG);
            assertThat(secondInput.readAllBytes()).isEqualTo(otherPng);
        }
    }

    @Test
    void rejectsMissingAndForgedStorageKeys() {
        LocalFileStorageService service = service();

        assertThatThrownBy(() -> service.open("../../outside.png"))
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> service.open("khong-phai-storage-key"))
                .isInstanceOf(InvalidFileException.class);
        assertThatThrownBy(() -> service.open("123e4567-e89b-42d3-a456-426614174000"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void validatesUploadCountForFutureRequestBoundary() {
        FileStorageValidator validator = new FileStorageValidator();
        MockMultipartFile file = new MockMultipartFile("file", "one.png", "image/png", PNG);

        validator.validateCount(List.of(file, file, file, file, file));

        assertThatThrownBy(() -> validator.validateCount(List.of(file, file, file, file, file, file)))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("5 files");
    }

    private LocalFileStorageService service() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setRoot(storageRoot);
        return new LocalFileStorageService(properties, new FileStorageValidator());
    }

    private static Stream<Arguments> validImages() {
        return Stream.of(
                Arguments.of("image/jpeg", JPEG),
                Arguments.of("image/png", PNG));
    }

    private static Stream<String> untrustedFilenames() {
        return Stream.of("../outside.png", "ảnh hiện trường (1).png", "a<>:|?*.png");
    }
}
