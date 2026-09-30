package com.tokentracker.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param username 3 to 50 letters, digits, dots, dashes or underscores (case-insensitive)
 * @param password 8 to 100 characters
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$", message = "must be 3 to 50 letters, digits, '.', '_' or '-'")
        String username,
        @NotBlank @Size(min = 8, max = 100, message = "must be 8 to 100 characters") String password) {
}
