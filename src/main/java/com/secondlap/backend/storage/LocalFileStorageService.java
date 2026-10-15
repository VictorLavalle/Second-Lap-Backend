package com.secondlap.backend.storage;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalFileStorageService implements StorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final LocalStorageProperties properties;

    public LocalFileStorageService(LocalStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(MultipartFile file) {
        validate(file);

        String extension = ALLOWED_TYPES.get(file.getContentType());
        String safeName = UUID.randomUUID() + "." + extension;
        Path baseDir = Path.of(properties.basePath()).toAbsolutePath().normalize();
        Path target = baseDir.resolve(safeName).normalize();

        // Defense in depth: a generated UUID name cannot traverse, but verify the
        // resolved path stays within the base directory before writing.
        if (!target.startsWith(baseDir)) {
            throw new InvalidUploadException("Resolved path escapes storage directory");
        }

        try {
            Files.createDirectories(baseDir);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }

        return properties.publicUrlPrefix() + "/" + safeName;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is required");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new InvalidUploadException(
                    "File exceeds maximum size of " + properties.maxFileSizeBytes() + " bytes");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new InvalidUploadException("Unsupported content type: " + contentType);
        }
    }
}
