package com.tokentracker.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Hourly price of a token, expressed in the configured price asset (WAVES).
 * When no trade happened during the hour, {@code price} carries the last known price
 * and {@code hasTrades} is false.
 */
@Entity
@Table(name = "token_price")
public class TokenPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    /** Start of the hour (UTC). */
    @Column(name = "price_time", nullable = false)
    private Instant priceTime;

    /** Null only while no trade has ever been found for the token. */
    @Column(precision = 38, scale = 12)
    private BigDecimal price;

    @Column(nullable = false, precision = 38, scale = 12)
    private BigDecimal volume;

    @Column(name = "has_trades", nullable = false)
    private boolean hasTrades;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public Instant getPriceTime() { return priceTime; }
    public void setPriceTime(Instant priceTime) { this.priceTime = priceTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public boolean isHasTrades() { return hasTrades; }
    public void setHasTrades(boolean hasTrades) { this.hasTrades = hasTrades; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
