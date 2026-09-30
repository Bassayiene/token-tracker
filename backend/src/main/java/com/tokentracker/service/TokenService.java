package com.tokentracker.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tokentracker.service.dto.HolderStatsDTO;
import com.tokentracker.service.dto.TokenPriceDTO;
import com.tokentracker.service.dto.TokenQuantityDTO;
import com.tokentracker.service.dto.TokenSummaryDTO;
import com.tokentracker.service.dto.TopHoldersDTO;

/**
 * Tracked tokens management and read access to the collected data.
 * Every method taking an asset id throws {@code NotFoundAlertException} when the token is not tracked.
 */
public interface TokenService {

    List<TokenSummaryDTO> findAll();

    TokenSummaryDTO findOne(String assetId);

    /**
     * Hourly prices in [from, to]. Defaults: the last 7 days.
     */
    List<TokenPriceDTO> getPrices(String assetId, Instant from, Instant to);

    /**
     * Daily quantities in [from, to]. Defaults: the last 90 days.
     */
    List<TokenQuantityDTO> getQuantities(String assetId, LocalDate from, LocalDate to);

    /**
     * Top holders snapshot of the given day, or of the latest day when {@code date} is null.
     */
    TopHoldersDTO getTopHolders(String assetId, LocalDate date);

    /**
     * Daily holder statistics in [from, to]. Defaults: the last 90 days.
     */
    List<HolderStatsDTO> getHolderStats(String assetId, LocalDate from, LocalDate to);

    /**
     * Starts tracking an asset: its invariant data is read once from the Waves Data API,
     * then its history is collected asynchronously.
     *
     * @throws com.tokentracker.exception.BadRequestAlertException if already tracked or unknown on Waves
     */
    TokenSummaryDTO addToken(String assetId);

    /** Stops tracking a token and deletes all its collected data. */
    void deleteToken(String assetId);
}
