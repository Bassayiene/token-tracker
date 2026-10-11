package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param assetId   {@code WAVES} for the native token
 * @param balance   balance adjusted with the asset decimals
 * @param updatedAt when this balance last changed
 */
public record AddressBalanceDTO(String assetId, String assetName, BigDecimal balance, Instant updatedAt) {
}
