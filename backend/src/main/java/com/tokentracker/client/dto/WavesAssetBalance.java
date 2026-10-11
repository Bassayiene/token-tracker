package com.tokentracker.client.dto;

/**
 * Balance of one asset held by an address, from the node endpoint {@code /assets/balance/{address}}.
 *
 * @param name     asset name, null when the node does not return the issue transaction (assets issued by a dApp)
 * @param decimals asset decimals, null in the same case
 * @param balance  raw on-chain amount
 */
public record WavesAssetBalance(String assetId, String name, Integer decimals, long balance) {
}
