package com.tokentracker.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Balance of one asset of a watched address on one day (UTC): the last balance observed that day.
 */
@Entity
@Table(name = "address_balance_daily")
public class AddressBalanceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watched_address_id", nullable = false)
    private Long watchedAddressId;

    /** {@code WAVES} for the native token. */
    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(nullable = false)
    private int decimals;

    @Column(nullable = false)
    private LocalDate date;

    /** Raw on-chain amount; 0 on the day the asset stopped being held. */
    @Column(nullable = false)
    private long balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getWatchedAddressId() { return watchedAddressId; }
    public void setWatchedAddressId(Long watchedAddressId) { this.watchedAddressId = watchedAddressId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public int getDecimals() { return decimals; }
    public void setDecimals(int decimals) { this.decimals = decimals; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
