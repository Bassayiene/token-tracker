package com.tokentracker.service;

import java.util.List;

import com.tokentracker.service.dto.SyncResultDTO;

/**
 * Collects token data from the Waves APIs and persists it.
 */
public interface TokenSyncService {

    /**
     * Hourly job: stores the price of every closed hour missing or recent (lookback window) for every token.
     * A token without any stored price gets the configured backfill instead.
     */
    List<SyncResultDTO> syncAllPrices();

    /**
     * Daily job: stores today's quantity, top holders and holder stats for every token.
     */
    List<SyncResultDTO> syncAllDaily();

    /**
     * Runs both collections for one token.
     *
     * @throws com.tokentracker.exception.NotFoundAlertException if the token is not tracked
     */
    SyncResultDTO syncToken(String assetId);

    /** Runs both collections for every token. */
    List<SyncResultDTO> syncAll();

    /**
     * Startup catch-up: fills missing hourly prices and today's daily data when absent.
     */
    void catchUp();

    /** Asynchronous {@link #syncToken(String)}, used right after a token is added. */
    void syncTokenAsync(String assetId);
}
