package com.secondlap.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secondlap.storage.local")
public record LocalStorageProperties(
        String basePath,
        String publicUrlPrefix,
        long maxFileSizeBytes
) {
}
