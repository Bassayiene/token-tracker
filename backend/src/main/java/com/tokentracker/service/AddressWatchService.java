package com.tokentracker.service;

import java.time.LocalDate;
import java.util.List;

import com.tokentracker.service.dto.AddressAssetVariationDTO;
import com.tokentracker.service.dto.AddressBalanceChangeDTO;
import com.tokentracker.service.dto.AddressBalanceDTO;
import com.tokentracker.service.dto.AddressCheckResultDTO;
import com.tokentracker.service.dto.AddressDailyBalanceDTO;
import com.tokentracker.service.dto.AddressHistoryAssetDTO;
import com.tokentracker.service.dto.WatchedAddressDTO;

/**
 * Watches addresses: compares their balances with the previous check and notifies the variations on Telegram.
 */
public interface AddressWatchService {

    List<WatchedAddressDTO> findAll();

    /**
     * Starts watching an address and stores its current balances as the baseline (no notification for those).
     *
     * @throws com.tokentracker.exception.BadRequestAlertException if the address is invalid, already watched,
     *                                                             or the limit is reached
     */
    WatchedAddressDTO add(String address, String label);

    /** Stops watching an address and deletes its balances and history. */
    void delete(Long id);

    /** Current balances of a watched address: WAVES first, then by asset name. */
    List<AddressBalanceDTO> getBalances(Long id);

    /** Most recent variations of a watched address, newest first. */
    List<AddressBalanceChangeDTO> getChanges(Long id, int limit);

    /** Assets with stored daily balances for the address, including those no longer held: WAVES first, then by name. */
    List<AddressHistoryAssetDTO> getHistoryAssets(Long id);

    /**
     * Daily balances of one asset of a watched address, oldest first. A day holds the last balance observed that day.
     *
     * @param from first day, default 90 days before {@code to}
     * @param to   last day, default today (UTC)
     */
    List<AddressDailyBalanceDTO> getHistory(Long id, String assetId, LocalDate from, LocalDate to);

    /**
     * Daily variation of the assets of a watched address, in percent of their balance on their first tracked day.
     * Only the assets whose balance differs from that reference at some point of the period are returned,
     * largest variation on the last day first.
     *
     * @param from  first day, default 90 days before {@code to}
     * @param to    last day, default today (UTC)
     * @param limit maximum number of assets returned
     */
    List<AddressAssetVariationDTO> getVariations(Long id, LocalDate from, LocalDate to, int limit);

    /** Scheduled job: checks every watched address. One failing address does not stop the others. */
    List<AddressCheckResultDTO> checkAll();

    /** Checks one address now. */
    AddressCheckResultDTO check(Long id);

    boolean isTelegramConfigured();

    /**
     * Sends a test message to the configured chat.
     *
     * @throws com.tokentracker.exception.TelegramException if it could not be delivered
     */
    void sendTelegramTest();
}
