package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A variation of one asset of a watched address. Amounts are adjusted with the asset decimals.
 *
 * @param newAsset the asset was not held at the previous check
 * @param notified whether Telegram accepted the notification
 */
public record AddressBalanceChangeDTO(
        Long id,
        String assetId,
        String assetName,
        BigDecimal oldBalance,
        BigDecimal newBalance,
        BigDecimal delta,
        boolean newAsset,
        Instant detectedAt,
        boolean notified) {
}
