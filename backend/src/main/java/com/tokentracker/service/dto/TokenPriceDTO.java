package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param time      start of the hour (UTC)
 * @param price     close price of the hour, or the last known price when {@code hasTrades} is false
 * @param volume    traded amount during the hour, in the token
 */
public record TokenPriceDTO(Instant time, BigDecimal price, BigDecimal volume, boolean hasTrades) {
}
