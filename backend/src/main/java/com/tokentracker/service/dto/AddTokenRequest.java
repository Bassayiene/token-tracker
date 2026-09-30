package com.tokentracker.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * @param assetId Waves asset id (base58)
 */
public record AddTokenRequest(
        @NotBlank
        @Pattern(regexp = "^[1-9A-HJ-NP-Za-km-z]{32,44}$", message = "must be a base58 Waves asset id")
        String assetId) {
}
