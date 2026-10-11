package com.tokentracker.web.rest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tokentracker.service.AddressWatchService;
import com.tokentracker.service.dto.AddWatchedAddressRequest;
import com.tokentracker.service.dto.AddressBalanceChangeDTO;
import com.tokentracker.service.dto.AddressBalanceDTO;
import com.tokentracker.service.dto.AddressCheckResultDTO;
import com.tokentracker.service.dto.WatchedAddressDTO;

import jakarta.validation.Valid;

/**
 * REST controller for the watched addresses and their Telegram notifications. Admin only (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/watched-addresses")
public class WatchedAddressResource {

    private static final Logger LOG = LoggerFactory.getLogger(WatchedAddressResource.class);

    private final AddressWatchService watchService;

    public WatchedAddressResource(AddressWatchService watchService) {
        this.watchService = watchService;
    }

    /** {@code GET /api/watched-addresses} : every watched address with the state of its last check. */
    @GetMapping
    public List<WatchedAddressDTO> getAll() {
        return watchService.findAll();
    }

    /** {@code POST /api/watched-addresses} : start watching an address; its current balances become the baseline. */
    @PostMapping
    public ResponseEntity<WatchedAddressDTO> add(@Valid @RequestBody AddWatchedAddressRequest request) {
        LOG.info("REST request to watch address {}", request.address());
        WatchedAddressDTO created = watchService.add(request.address(), request.label());
        return ResponseEntity.created(URI.create("/api/watched-addresses/" + created.id())).body(created);
    }

    /** {@code DELETE /api/watched-addresses/{id}} : stop watching an address and delete its history. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.info("REST request to stop watching address {}", id);
        watchService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** {@code GET /api/watched-addresses/{id}/balances} : balances stored by the last check. */
    @GetMapping("/{id}/balances")
    public List<AddressBalanceDTO> getBalances(@PathVariable Long id) {
        return watchService.getBalances(id);
    }

    /** {@code GET /api/watched-addresses/{id}/changes?limit=} : latest variations, newest first (default 100). */
    @GetMapping("/{id}/changes")
    public List<AddressBalanceChangeDTO> getChanges(@PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        return watchService.getChanges(id, limit);
    }

    /** {@code POST /api/watched-addresses/{id}/check} : check one address now. */
    @PostMapping("/{id}/check")
    public AddressCheckResultDTO check(@PathVariable Long id) {
        LOG.info("REST request to check watched address {}", id);
        return watchService.check(id);
    }

    /** {@code GET /api/watched-addresses/telegram} : whether a bot token and a chat id are configured. */
    @GetMapping("/telegram")
    public Map<String, Boolean> telegramStatus() {
        return Map.of("configured", watchService.isTelegramConfigured());
    }

    /** {@code POST /api/watched-addresses/telegram/test} : send a test message to the configured chat. */
    @PostMapping("/telegram/test")
    public ResponseEntity<Void> telegramTest() {
        LOG.info("REST request to send a Telegram test message");
        watchService.sendTelegramTest();
        return ResponseEntity.noContent().build();
    }
}
