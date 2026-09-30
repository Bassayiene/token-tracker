package com.tokentracker.config;

import java.time.Clock;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /** Shared client for the Waves Data API and the Waves node. */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, TokenTrackerProperties properties) {
        return builder
                .connectTimeout(properties.waves().connectTimeout())
                .readTimeout(properties.waves().readTimeout())
                .build();
    }

    /** Injected so that time-dependent logic (closed hours, today's date) is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
