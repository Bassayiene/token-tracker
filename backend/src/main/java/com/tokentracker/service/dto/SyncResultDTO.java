package com.tokentracker.service.dto;

/**
 * Outcome of a synchronisation for one token.
 *
 * @param priceHours number of hourly price rows written
 * @param dailyDone  whether quantity, top holders and holder stats were written
 * @param error      error message when (part of) the synchronisation failed, null otherwise
 */
public record SyncResultDTO(String assetId, int priceHours, boolean dailyDone, String error) {
}
