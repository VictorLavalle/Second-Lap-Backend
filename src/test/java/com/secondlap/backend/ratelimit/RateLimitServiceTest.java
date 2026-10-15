package com.secondlap.backend.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private RateLimitProperties props(long cap) {
        RateLimitProperties.Tier tier = new RateLimitProperties.Tier(cap, 60);
        return new RateLimitProperties(tier, tier, tier, tier);
    }

    @Test
    void allowsUpToCapacityThenDeniesPerIp() {
        RateLimitService service = new RateLimitService(props(3));

        assertThat(service.tryConsumeOrderByIp("1.1.1.1")).isTrue();
        assertThat(service.tryConsumeOrderByIp("1.1.1.1")).isTrue();
        assertThat(service.tryConsumeOrderByIp("1.1.1.1")).isTrue();
        assertThat(service.tryConsumeOrderByIp("1.1.1.1")).isFalse(); // 4th exceeds -> would be 429
    }

    @Test
    void tracksSeparateBucketsPerKey() {
        RateLimitService service = new RateLimitService(props(1));

        assertThat(service.tryConsumeOrderByWhatsapp("9990000000")).isTrue();
        assertThat(service.tryConsumeOrderByWhatsapp("9990000000")).isFalse();
        // a different number has its own fresh bucket
        assertThat(service.tryConsumeOrderByWhatsapp("9991111111")).isTrue();
    }

    @Test
    void tiersAreIndependent() {
        RateLimitService service = new RateLimitService(props(1));
        String ip = "2.2.2.2";

        assertThat(service.tryConsumeOrderByIp(ip)).isTrue();
        assertThat(service.tryConsumeOrderByIp(ip)).isFalse();
        // reads and admin-login tiers are not affected by the orders bucket
        assertThat(service.tryConsumePublicRead(ip)).isTrue();
        assertThat(service.tryConsumeAdminLogin(ip)).isTrue();
    }
}
