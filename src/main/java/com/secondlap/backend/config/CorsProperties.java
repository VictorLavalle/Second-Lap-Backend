package com.secondlap.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "secondlap.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
