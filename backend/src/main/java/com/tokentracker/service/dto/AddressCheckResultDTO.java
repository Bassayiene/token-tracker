package com.tokentracker.service.dto;

/**
 * Outcome of one check of a watched address.
 *
 * @param changes  number of variations detected by this check
 * @param notified whether every pending variation was sent to Telegram (true when there was nothing to send)
 * @param error    node or Telegram error, null when everything succeeded
 */
public record AddressCheckResultDTO(Long id, String address, int changes, boolean notified, String error) {
}
