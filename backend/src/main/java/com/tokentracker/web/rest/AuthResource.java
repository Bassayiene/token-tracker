package com.tokentracker.web.rest;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tokentracker.config.SecurityConfig;
import com.tokentracker.domain.Role;
import com.tokentracker.service.AccountService;
import com.tokentracker.service.dto.AccountDTO;
import com.tokentracker.service.dto.AuthResponseDTO;
import com.tokentracker.service.dto.LoginRequest;
import com.tokentracker.service.dto.RegisterRequest;

import jakarta.validation.Valid;

/**
 * REST controller for registration, login and the current account.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AccountService accountService;

    public AuthResource(AccountService accountService) {
        this.accountService = accountService;
    }

    /** {@code POST /api/auth/register} : create a USER account and return its token. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponseDTO response = accountService.register(request.username(), request.password());
        return ResponseEntity.created(URI.create("/api/auth/me")).body(response);
    }

    /** {@code POST /api/auth/login} : exchange credentials for a token. */
    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequest request) {
        return accountService.login(request.username(), request.password());
    }

    /** {@code GET /api/auth/me} : the account of the presented token. */
    @GetMapping("/me")
    public AccountDTO me(@AuthenticationPrincipal Jwt jwt) {
        return new AccountDTO(jwt.getSubject(), Role.valueOf(jwt.getClaimAsString(SecurityConfig.ROLE_CLAIM)));
    }
}
