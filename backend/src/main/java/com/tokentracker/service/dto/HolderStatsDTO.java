package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param top10Share  percentage of the quantity held by the 10 largest holders
 * @param top100Share percentage of the quantity held by the 100 largest holders
 */
public record HolderStatsDTO(LocalDate date, int holdersCount, BigDecimal top10Share, BigDecimal top100Share) {
}
