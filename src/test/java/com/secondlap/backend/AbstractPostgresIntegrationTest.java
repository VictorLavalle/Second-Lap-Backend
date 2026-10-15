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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

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
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("secondlap.cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("secondlap.admin.username", () -> "admin");
        registry.add("secondlap.admin.password", () -> "admin123");
    }
}
