package com.tokentracker.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param rawQuantity quantity as stored on-chain
 * @param quantity    quantity adjusted with the token decimals
 */
public record TokenQuantityDTO(LocalDate date, long rawQuantity, BigDecimal quantity) {
}
