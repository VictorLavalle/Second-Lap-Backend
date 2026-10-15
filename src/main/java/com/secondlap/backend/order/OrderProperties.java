package com.secondlap.backend.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secondlap.order")
public record OrderProperties(int maxItems) {
}
