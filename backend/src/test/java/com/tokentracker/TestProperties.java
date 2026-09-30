package com.tokentracker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

import com.tokentracker.config.TokenTrackerProperties;

/**
 * Helpers shared by unit tests.
 */
public final class TestProperties {

    public static final String DATA_API_URL = "https://api.test/v0";
    public static final String NODE_URL = "https://node.test";

    private TestProperties() {
    }

    public static TokenTrackerProperties properties(int topHoldersLimit, int lookbackHours, int backfillDays) {
        return new TokenTrackerProperties(
                "WAVES",
                topHoldersLimit,
                new TokenTrackerProperties.Waves(DATA_API_URL, NODE_URL, 1000, Duration.ofSeconds(5), Duration.ofSeconds(30)),
                new TokenTrackerProperties.Sync("-", "-", lookbackHours, backfillDays, 10, 365, false),
                new TokenTrackerProperties.Cors(List.of("http://localhost:4200")),
                new TokenTrackerProperties.Security(null, Duration.ofHours(1), "admin", null));
    }

    public static String fixture(String name) {
        try {
            return new ClassPathResource("waves/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
