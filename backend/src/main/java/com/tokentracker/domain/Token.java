package com.tokentracker.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A tracked token. Holds invariant data only, written once when the token is added.
 */
@Entity
@Table(name = "token")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, unique = true, length = 64)
    private String assetId;

    @Column(nullable = false)
    private String name;

    @Column(length = 32)
    private String ticker;

    @Column(nullable = false)
    private int decimals;

    @Column(length = 1000)
    private String description;

    /** On-chain issue date of the asset. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean reissuable;

    @Column(name = "has_script", nullable = false)
    private boolean hasScript;

    public Long getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public int getDecimals() { return decimals; }
    public void setDecimals(int decimals) { this.decimals = decimals; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isReissuable() { return reissuable; }
    public void setReissuable(boolean reissuable) { this.reissuable = reissuable; }
    public boolean isHasScript() { return hasScript; }
    public void setHasScript(boolean hasScript) { this.hasScript = hasScript; }
}
