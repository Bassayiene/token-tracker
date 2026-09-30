package com.tokentracker.service;

import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tokentracker.config.SecurityConfig;
import com.tokentracker.config.TokenTrackerProperties;
import com.tokentracker.domain.AppUser;
import com.tokentracker.domain.Role;
import com.tokentracker.exception.BadRequestAlertException;
import com.tokentracker.repository.AppUserRepository;
import com.tokentracker.service.dto.AuthResponseDTO;

/**
 * Accounts: self-registration, login (JWT issuing) and the admin account created from the configuration.
 */
@Service
public class AccountService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final TokenTrackerProperties properties;
    /** Compared against when the username is unknown, so both failures take the same time. */
    private final String dummyHash;

    public AccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            TokenTrackerProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Creates a {@link Role#USER} account and logs it in. */
    @Transactional
    public AuthResponseDTO register(String username, String password) {
        String normalized = normalize(username);
        if (userRepository.existsByUsername(normalized)) {
            throw new BadRequestAlertException("This username is already taken", "account", "usernameexists");
        }
        AppUser user;
        try {
            user = create(normalized, password, Role.USER);
        } catch (DataIntegrityViolationException e) {
            // Same username registered concurrently
            throw new BadRequestAlertException("This username is already taken", "account", "usernameexists");
        }
        LOG.info("Registered account {}", normalized);
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(String username, String password) {
        AppUser user = userRepository.findByUsername(normalize(username)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!passwordEncoder.matches(password, hash) || user == null) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return issueToken(user);
    }

    /**
     * Creates the configured admin account when it does not exist, or gives it back the ADMIN role.
     * An existing password is never overwritten.
     */
    @Transactional
    public void ensureAdminAccount() {
        String username = normalize(properties.security().adminUsername());
        String password = properties.security().adminPassword();
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                LOG.info("Account {} promoted to ADMIN", username);
            }
        }, () -> {
            if (password == null || password.isBlank()) {
                LOG.warn("token-tracker.security.admin-password is not set: no admin account is created. "
                        + "Set ADMIN_PASSWORD to be able to add tokens and trigger synchronisations.");
                return;
            }
            create(username, password, Role.ADMIN);
            LOG.info("Admin account {} created", username);
        });
    }

    private AppUser create(String username, String password, Role role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setCreatedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private AuthResponseDTO issueToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.security().tokenValidity());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponseDTO(token, user.getUsername(), user.getRole(), expiresAt);
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
