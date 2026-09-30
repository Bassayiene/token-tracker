package com.tokentracker.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Typed binding of the {@code token-tracker.*} properties.
 */
@Validated
@ConfigurationProperties(prefix = "token-tracker")
public record TokenTrackerProperties(
        @NotBlank String priceAsset,
        @Min(1) int topHoldersLimit,
        @Valid @NotNull Waves waves,
        @Valid @NotNull Sync sync,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security) {

    public record Waves(
            @NotBlank String dataApiUrl,
            @NotBlank String nodeUrl,
            @Min(1) int distributionPageSize,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }

    public record Sync(
            @NotBlank String priceCron,
            @NotBlank String dailyCron,
            @Min(1) int priceLookbackHours,
            @Min(1) int priceBackfillDays,
            @Min(1) int priceChunkDays,
            @Min(1) int lastTradeSearchDays,
            boolean runOnStartup) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param jwtSecret     base64 HMAC key of at least 256 bits; when blank a random key is generated at startup,
     *                      so every issued token becomes invalid on restart
     * @param tokenValidity lifetime of an issued token
     * @param adminUsername account created with the ADMIN role at startup when it does not exist yet
     * @param adminPassword password of that account; when blank no admin account is created
     */
    public record Security(
            String jwtSecret,
            @NotNull Duration tokenValidity,
            @NotBlank String adminUsername,
            String adminPassword) {
    }
}
