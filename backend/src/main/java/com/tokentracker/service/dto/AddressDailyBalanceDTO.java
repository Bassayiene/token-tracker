package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param date    day (UTC)
 * @param balance last balance observed that day, adjusted with the asset decimals
 */
public record AddressDailyBalanceDTO(LocalDate date, BigDecimal balance) {
}
