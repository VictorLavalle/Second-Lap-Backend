package com.secondlap.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secondlap.cache.public-reads")
public record CacheProperties(long maxAgeSeconds) {
}
