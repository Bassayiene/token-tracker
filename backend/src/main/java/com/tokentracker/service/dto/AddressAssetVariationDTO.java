package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily variation of one asset of a watched address, in percent of its balance on its first tracked day.
 *
 * @param baseDate    first day with a stored balance for this asset: the 0 % reference
 * @param baseBalance balance on that day, adjusted with the asset decimals
 * @param lastPercent variation on the last day of the requested period
 * @param points      one point per stored day of the requested period, oldest first
 */
public record AddressAssetVariationDTO(
        String assetId,
        String assetName,
        LocalDate baseDate,
        BigDecimal baseBalance,
        BigDecimal lastPercent,
        List<Point> points) {

    /**
     * @param percent {@code (balance - baseBalance) / baseBalance * 100}; -100 when the asset is no longer held
     */
    public record Point(LocalDate date, BigDecimal percent) {
    }
}
