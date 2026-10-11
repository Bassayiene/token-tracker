package com.tokentracker.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A variation detected between two checks of a watched address.
 */
@Entity
@Table(name = "address_balance_change")
public class AddressBalanceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watched_address_id", nullable = false)
    private Long watchedAddressId;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    @Column(nullable = false)
    private int decimals;

    @Column(name = "old_balance", nullable = false)
    private long oldBalance;

    @Column(name = "new_balance", nullable = false)
    private long newBalance;

    /** The asset was not held at the previous check. */
    @Column(name = "new_asset", nullable = false)
    private boolean newAsset;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** False until Telegram accepted the message, so the notification is retried. */
    @Column(nullable = false)
    private boolean notified;

    public Long getId() { return id; }
    public Long getWatchedAddressId() { return watchedAddressId; }
    public void setWatchedAddressId(Long watchedAddressId) { this.watchedAddressId = watchedAddressId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public int getDecimals() { return decimals; }
    public void setDecimals(int decimals) { this.decimals = decimals; }
    public long getOldBalance() { return oldBalance; }
    public void setOldBalance(long oldBalance) { this.oldBalance = oldBalance; }
    public long getNewBalance() { return newBalance; }
    public void setNewBalance(long newBalance) { this.newBalance = newBalance; }
    public boolean isNewAsset() { return newAsset; }
    public void setNewAsset(boolean newAsset) { this.newAsset = newAsset; }
    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }
}
