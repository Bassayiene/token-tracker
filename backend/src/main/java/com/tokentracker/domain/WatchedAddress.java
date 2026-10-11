package com.tokentracker.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An address whose balances are checked periodically. Variations are notified on Telegram.
 */
@Entity
@Table(name = "watched_address")
public class WatchedAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String address;

    @Column(length = 100)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Last successful check; null until the first snapshot (baseline) is stored. */
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    /** Error of the last check, null when it succeeded. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    public Long getId() { return id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
