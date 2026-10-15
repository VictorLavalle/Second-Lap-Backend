package com.secondlap.backend.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limit buckets expressed as "capacity tokens per window seconds". Defaults
 * are conservative for a small storefront and overridable per environment.
 */
@ConfigurationProperties(prefix = "secondlap.ratelimit")
public record RateLimitProperties(
        Tier publicReads,
        Tier orders,
        Tier ordersPerWhatsapp,
        Tier adminLogin
) {

    public record Tier(long capacity, long windowSeconds) {
    }
}
