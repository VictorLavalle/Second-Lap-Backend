package com.secondlap.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private LocalStorageProperties propertiesFor(Path baseDir) {
        return new LocalStorageProperties(baseDir.toString(), "/uploads", 1_000_000L);
    }

    @Test
    void writesFileToLocalDiskAndReturnsPublicUrl(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.png", "image/png", new byte[]{1, 2, 3, 4});

        String url = service.store(file);

        assertThat(url).startsWith("/uploads/").endsWith(".png");
        String storedName = url.substring("/uploads/".length());
        Path writtenFile = baseDir.resolve(storedName);
        assertThat(Files.exists(writtenFile)).isTrue();
    }

    @Test
    void rejectsUnsupportedContentType(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(pdf))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void rejectsFileExceedingMaxSize(@TempDir Path baseDir) {
        LocalStorageProperties tinyLimit =
                new LocalStorageProperties(baseDir.toString(), "/uploads", 2L);
        LocalFileStorageService service = new LocalFileStorageService(tinyLimit);
        MockMultipartFile tooBig = new MockMultipartFile(
                "files", "big.jpg", "image/jpeg", new byte[]{1, 2, 3, 4, 5});

        assertThatThrownBy(() -> service.store(tooBig))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    void rejectsEmptyFile(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile empty = new MockMultipartFile(
                "files", "empty.png", "image/png", new byte[]{});

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsNullFile(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));

        assertThatThrownBy(() -> service.store(null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsMissingContentType(@TempDir Path baseDir) {
        LocalFileStorageService service = new LocalFileStorageService(propertiesFor(baseDir));
        MockMultipartFile noType = new MockMultipartFile(
                "files", "file.bin", null, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(noType))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void wrapsIoFailureWhenBasePathIsNotADirectory(@TempDir Path baseDir) throws Exception {
        // Make the base path a regular file so createDirectories fails with IOException.
        Path asFile = baseDir.resolve("not-a-dir");
        java.nio.file.Files.writeString(asFile, "x");
        LocalStorageProperties props =
                new LocalStorageProperties(asFile.toString(), "/uploads", 1_000_000L);
        LocalFileStorageService service = new LocalFileStorageService(props);
        MockMultipartFile img = new MockMultipartFile(
                "files", "p.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.store(img))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }
}
