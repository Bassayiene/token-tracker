package com.tokentracker.service.dto;

import java.time.LocalDate;

/**
 * An asset whose daily balances are stored for a watched address.
 *
 * @param firstDate first day with a stored balance
 * @param lastDate  last day with a stored balance; earlier than today when the asset is no longer held
 */
public record AddressHistoryAssetDTO(String assetId, String assetName, LocalDate firstDate, LocalDate lastDate) {
}
