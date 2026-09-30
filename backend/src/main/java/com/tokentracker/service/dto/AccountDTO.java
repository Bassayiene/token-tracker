package com.tokentracker.service.dto;

import com.tokentracker.domain.Role;

public record AccountDTO(String username, Role role) {
}
