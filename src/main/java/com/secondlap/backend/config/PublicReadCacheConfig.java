package com.secondlap.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class PublicReadCacheConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public PublicReadCacheFilter publicReadCacheFilter(CacheProperties cacheProperties) {
        return new PublicReadCacheFilter(cacheProperties.maxAgeSeconds());
    }
}
