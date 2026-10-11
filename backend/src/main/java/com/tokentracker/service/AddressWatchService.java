package com.tokentracker.service;

import java.util.List;

import com.tokentracker.service.dto.AddressBalanceChangeDTO;
import com.tokentracker.service.dto.AddressBalanceDTO;
import com.tokentracker.service.dto.AddressCheckResultDTO;
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
