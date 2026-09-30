package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Top holders snapshot of a token.
 *
 * @param date           snapshot day, null when no snapshot exists yet
 * @param previousDate   snapshot the changes are computed against, null if none
 * @param height         block height at which the distribution was read
 * @param totalQuantity  quantity used to compute the shares, adjusted with the decimals
 * @param availableDates every snapshot day, newest first
 */
public record TopHoldersDTO(
        LocalDate date,
        LocalDate previousDate,
        Integer height,
        BigDecimal totalQuantity,
        List<TopHolderDTO> holders,
        List<LocalDate> availableDates) {
}
