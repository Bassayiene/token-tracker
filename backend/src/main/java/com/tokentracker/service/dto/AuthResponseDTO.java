package com.tokentracker.service.dto;

import java.time.Instant;

import com.tokentracker.domain.Role;

/**
 * @param token     bearer token to send in the {@code Authorization} header
 * @param expiresAt instant after which the token is rejected
 */
public record AuthResponseDTO(String token, String username, Role role, Instant expiresAt) {
}
