package com.tokentracker.client.dto;

import java.util.Map;

/**
 * One page of the node endpoint {@code /assets/{id}/distribution/{height}/limit/{limit}}.
 *
 * @param balances address -> raw balance, in the order returned by the node (not sorted by balance)
 * @param hasNext  whether another page follows
 * @param lastItem cursor to pass as {@code after} to read the next page
 */
public record WavesDistributionPage(Map<String, Long> balances, boolean hasNext, String lastItem) {
}
