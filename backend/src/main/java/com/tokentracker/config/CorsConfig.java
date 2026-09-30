package com.tokentracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend calls this service directly (no gateway), so CORS is handled here.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final TokenTrackerProperties properties;

    public CorsConfig(TokenTrackerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.cors().allowedOrigins() == null || properties.cors().allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(1800);
    }
}
