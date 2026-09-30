package com.tokentracker.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One candle of the Waves Data API ({@code /candles/{amountAsset}/{priceAsset}}).
 * Prices and volume are null when no trade happened during the interval.
 *
 * @param time     start of the interval (UTC)
 * @param volume   traded amount, in the amount asset
 * @param txsCount number of trades during the interval
 */
public record WavesCandle(
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        int txsCount) {

    public boolean hasTrades() {
        return txsCount > 0 && close != null;
    }
}
