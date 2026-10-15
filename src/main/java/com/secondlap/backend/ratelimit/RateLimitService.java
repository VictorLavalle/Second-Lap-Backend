package com.secondlap.backend.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one token bucket per (tier, key) pair in memory. Keys are opaque
 * (client IP or normalized WhatsApp), so the same client is throttled
 * consistently across requests. In-memory is intentional: this is per-instance
 * throttling to curb abuse, not a distributed quota — volumetric DDoS is handled
 * upstream in infrastructure.
 */
@Service
public class RateLimitService {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean tryConsumePublicRead(String ip) {
        return bucketFor("read", ip, properties.publicReads()).tryConsume(1);
    }

    public boolean tryConsumeOrderByIp(String ip) {
        return bucketFor("order-ip", ip, properties.orders()).tryConsume(1);
    }

    public boolean tryConsumeOrderByWhatsapp(String whatsapp) {
        return bucketFor("order-wa", whatsapp, properties.ordersPerWhatsapp()).tryConsume(1);
    }

    public boolean tryConsumeAdminLogin(String ip) {
        return bucketFor("admin-login", ip, properties.adminLogin()).tryConsume(1);
    }

    private Bucket bucketFor(String tier, String key, RateLimitProperties.Tier config) {
        return buckets.computeIfAbsent(tier + ":" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(config.capacity())
                        .refillGreedy(config.capacity(), Duration.ofSeconds(config.windowSeconds()))
                        .build())
                .build());
    }
}
