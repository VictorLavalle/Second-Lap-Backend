package com.secondlap.backend.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secondlap.admin")
public record AdminProperties(String username, String password) {
}
