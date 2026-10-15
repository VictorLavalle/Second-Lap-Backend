package com.secondlap.backend.config;

import com.secondlap.backend.storage.LocalStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(LocalStorageProperties.class)
public class StaticResourceConfig implements WebMvcConfigurer {

    private final LocalStorageProperties storageProperties;

    public StaticResourceConfig(LocalStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageProperties.basePath()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(storageProperties.publicUrlPrefix() + "/**")
                .addResourceLocations(location);
    }
}
