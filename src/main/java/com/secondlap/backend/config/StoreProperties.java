package com.secondlap.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secondlap.store")
public record StoreProperties(String whatsapp) {
}
