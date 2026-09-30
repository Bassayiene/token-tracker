package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A tracked token with its latest collected figures.
 *
 * @param priceAsset       asset in which prices are expressed (WAVES)
 * @param lastPrice        price of the most recent stored hour (carried forward when no trade)
 * @param lastPriceTime    most recent stored hour
 * @param lastTradeTime    most recent hour with a real trade
 * @param change24h        price variation over 24 hours, in percent (null if not computable)
 * @param change7d         price variation over 7 days, in percent (null if not computable)
 * @param quantity         latest issued quantity, adjusted with the decimals
 * @param holdersCount     latest number of addresses with a positive balance
 * @param top10Share       latest share of the quantity held by the top 10, in percent
 * @param top100Share      latest share of the quantity held by the top 100, in percent
 */
public record TokenSummaryDTO(
        String assetId,
        String name,
        String ticker,
        int decimals,
        String description,
        Instant createdAt,
        boolean reissuable,
        boolean hasScript,
        String priceAsset,
        BigDecimal lastPrice,
        Instant lastPriceTime,
        Instant lastTradeTime,
        BigDecimal change24h,
        BigDecimal change7d,
        BigDecimal quantity,
        LocalDate quantityDate,
        Integer holdersCount,
        BigDecimal top10Share,
        BigDecimal top100Share,
        LocalDate holdersDate) {

    /** Same summary without the holder figures, which are reserved to authenticated users. */
    public TokenSummaryDTO withoutHolderStats() {
        return new TokenSummaryDTO(assetId, name, ticker, decimals, description, createdAt, reissuable, hasScript,
                priceAsset, lastPrice, lastPriceTime, lastTradeTime, change24h, change7d, quantity, quantityDate,
                null, null, null, null);
    }
}
