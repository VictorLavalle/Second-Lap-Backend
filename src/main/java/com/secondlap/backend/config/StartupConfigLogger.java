package com.secondlap.backend.config;

import com.secondlap.backend.storage.LocalStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the effective runtime configuration once at startup so the active
 * profile, port and key non-secret settings are visible when debugging an
 * environment. Secrets (DB password, admin password) are never logged.
 */
@Component
public class StartupConfigLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigLogger.class);

    private final Environment environment;
    private final CorsProperties corsProperties;
    private final LocalStorageProperties storageProperties;

    public StartupConfigLogger(Environment environment, CorsProperties corsProperties,
                               LocalStorageProperties storageProperties) {
        this.environment = environment;
        this.corsProperties = corsProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String profiles = String.join(",", environment.getActiveProfiles());
        log.info("Startup config — profile(s): [{}], port: {}, CORS origins: {}, uploads dir: {}",
                profiles.isBlank() ? "default" : profiles,
                environment.getProperty("server.port", "?"),
                corsProperties.allowedOrigins(),
                storageProperties.basePath());
    }
}
