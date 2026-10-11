package com.tokentracker.service.dto;

import java.time.Instant;

/**
 * @param lastCheckedAt last successful check, null before the first one
 * @param lastError     error of the last check (node or Telegram), null when it succeeded
 * @param assetsCount   number of assets currently held, WAVES included
 */
public record WatchedAddressDTO(
        Long id,
        String address,
        String label,
        Instant createdAt,
        Instant lastCheckedAt,
        String lastError,
        long assetsCount) {
}
