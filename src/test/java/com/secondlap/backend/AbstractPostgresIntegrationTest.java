package com.secondlap.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    // Singleton container: started once and shared by every IT class. Using the
    // JUnit @Container lifecycle on a static field in a base class stops the
    // container when the first class finishes, breaking the rest; a manually
    // started singleton stays up for the whole suite and is reaped by Ryuk.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            // Several @SpringBootTest contexts (different @TestPropertySource) each
            // open their own pool; raise the server limit so they coexist and the
            // concurrency IT has head-room for REQUIRES_NEW sub-transactions.
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }

    @Autowired
    private Flyway flyway;

    // Tests mutate product/order state; reset to the seeded baseline before each
    // test so cases that reserve products do not leak into read-only assertions.
    @BeforeEach
    void resetToSeedBaseline() {
        flyway.clean();
        flyway.migrate();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Concurrency ITs run dozens of threads that each may need a second
        // connection for the REQUIRES_NEW customer insert; size the pool above
        // the thread count so the test exercises row-lock contention, not pool
        // starvation.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("secondlap.cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("secondlap.admin.username", () -> "admin");
        registry.add("secondlap.admin.password", () -> "admin123");
        // High rate-limit ceilings by default so unrelated ITs sharing the
        // per-instance buckets never trip the limiter; SecurityHardeningIT
        // overrides these with low values in its own context.
        registry.add("secondlap.ratelimit.public-reads.capacity", () -> "100000");
        registry.add("secondlap.ratelimit.orders.capacity", () -> "100000");
        registry.add("secondlap.ratelimit.orders-per-whatsapp.capacity", () -> "100000");
        registry.add("secondlap.ratelimit.admin-login.capacity", () -> "100000");
    }
}
