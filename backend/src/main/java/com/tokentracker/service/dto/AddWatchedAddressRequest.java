package com.tokentracker.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param address Waves address (base58, 35 characters); the node then checks its checksum and network
 * @param label   optional name shown in the notifications
 */
public record AddWatchedAddressRequest(
        @NotBlank
        @Pattern(regexp = "^[1-9A-HJ-NP-Za-km-z]{35}$", message = "must be a base58 Waves address")
        String address,
        @Size(max = 100) String label) {
}
