package com.tokentracker.service.dto;

import java.math.BigDecimal;

/**
 * @param balance       balance adjusted with the token decimals
 * @param share         percentage of the total quantity
 * @param balanceChange balance variation since the previous snapshot (null if unknown or new)
 * @param rankChange    positions gained (positive) or lost (negative) since the previous snapshot
 * @param isNew         the address was not in the previous snapshot
 */
public record TopHolderDTO(
        int rank,
        String address,
        BigDecimal balance,
        BigDecimal share,
        BigDecimal balanceChange,
        Integer rankChange,
        boolean isNew) {
}
