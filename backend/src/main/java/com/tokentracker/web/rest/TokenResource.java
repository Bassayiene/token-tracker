package com.tokentracker.web.rest;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tokentracker.service.TokenService;
import com.tokentracker.service.TokenSyncService;
import com.tokentracker.service.dto.AddTokenRequest;
import com.tokentracker.service.dto.HolderStatsDTO;
import com.tokentracker.service.dto.SyncResultDTO;
import com.tokentracker.service.dto.TokenPriceDTO;
import com.tokentracker.service.dto.TokenQuantityDTO;
import com.tokentracker.service.dto.TokenSummaryDTO;
import com.tokentracker.service.dto.TopHoldersDTO;

import jakarta.validation.Valid;

/**
 * REST controller for tracked tokens and their collected data. Access rules per endpoint are in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/tokens")
public class TokenResource {

    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);

    private final TokenService tokenService;
    private final TokenSyncService syncService;

    public TokenResource(TokenService tokenService, TokenSyncService syncService) {
        this.tokenService = tokenService;
        this.syncService = syncService;
    }

    /** {@code GET /api/tokens} : every tracked token with its latest figures (holder figures for users only). */
    @GetMapping
    public List<TokenSummaryDTO> getAll(Principal principal) {
        List<TokenSummaryDTO> tokens = tokenService.findAll();
        return principal != null ? tokens : tokens.stream().map(TokenSummaryDTO::withoutHolderStats).toList();
    }

    /** {@code GET /api/tokens/{assetId}} : one tracked token with its latest figures (holder figures for users only). */
    @GetMapping("/{assetId}")
    public TokenSummaryDTO getOne(@PathVariable String assetId, Principal principal) {
        TokenSummaryDTO token = tokenService.findOne(assetId);
        return principal != null ? token : token.withoutHolderStats();
    }

    /** {@code GET /api/tokens/{assetId}/prices?from=&to=} : hourly prices (ISO-8601 instants, default last 7 days). */
    @GetMapping("/{assetId}/prices")
    public List<TokenPriceDTO> getPrices(@PathVariable String assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return tokenService.getPrices(assetId, from, to);
    }

    /** {@code GET /api/tokens/{assetId}/quantities?from=&to=} : daily quantities (ISO dates, default last 90 days). */
    @GetMapping("/{assetId}/quantities")
    public List<TokenQuantityDTO> getQuantities(@PathVariable String assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tokenService.getQuantities(assetId, from, to);
    }

    /** {@code GET /api/tokens/{assetId}/holders?date=} : top holders of a day (default latest snapshot). */
    @GetMapping("/{assetId}/holders")
    public TopHoldersDTO getTopHolders(@PathVariable String assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tokenService.getTopHolders(assetId, date);
    }

    /** {@code GET /api/tokens/{assetId}/holder-stats?from=&to=} : daily holder statistics (default last 90 days). */
    @GetMapping("/{assetId}/holder-stats")
    public List<HolderStatsDTO> getHolderStats(@PathVariable String assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tokenService.getHolderStats(assetId, from, to);
    }

    /** {@code POST /api/tokens} : start tracking an asset; its history is collected in the background. */
    @PostMapping
    public ResponseEntity<TokenSummaryDTO> addToken(@Valid @RequestBody AddTokenRequest request) {
        LOG.info("REST request to add token {}", request.assetId());
        TokenSummaryDTO token = tokenService.addToken(request.assetId());
        return ResponseEntity.created(URI.create("/api/tokens/" + token.assetId())).body(token);
    }

    /** {@code DELETE /api/tokens/{assetId}} : stop tracking a token and delete its data. */
    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteToken(@PathVariable String assetId) {
        LOG.info("REST request to delete token {}", assetId);
        tokenService.deleteToken(assetId);
        return ResponseEntity.noContent().build();
    }

    /** {@code POST /api/tokens/sync} : run every collection now for every token. */
    @PostMapping("/sync")
    public List<SyncResultDTO> syncAll() {
        LOG.info("REST request to sync all tokens");
        return syncService.syncAll();
    }

    /** {@code POST /api/tokens/{assetId}/sync} : run every collection now for one token. */
    @PostMapping("/{assetId}/sync")
    public SyncResultDTO syncOne(@PathVariable String assetId) {
        LOG.info("REST request to sync token {}", assetId);
        return syncService.syncToken(assetId);
    }
}
