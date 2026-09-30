package com.tokentracker.client.dto;

import java.time.Instant;

/**
 * Asset description returned by the Waves Data API ({@code /assets/{id}}).
 *
 * @param quantity raw issued quantity (divide by 10^decimals to display)
 * @param issuedAt on-chain issue date
 */
public record WavesAsset(
        String assetId,
        String name,
        String ticker,
        int decimals,
        String description,
        Instant issuedAt,
        long quantity,
        boolean reissuable,
        boolean hasScript) {
}
