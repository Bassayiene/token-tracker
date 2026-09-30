package com.tokentracker.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Daily holder statistics of a token, computed from the full distribution scan.
 */
@Entity
@Table(name = "token_holder_stats")
public class TokenHolderStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    /** UTC day of the snapshot. */
    @Column(nullable = false)
    private LocalDate date;

    /** Number of addresses with a positive balance. */
    @Column(name = "holders_count", nullable = false)
    private int holdersCount;

    /** Percentage (0..100) of the total quantity held by the 10 largest holders. */
    @Column(name = "top10_share", nullable = false, precision = 9, scale = 4)
    private BigDecimal top10Share;

    /** Percentage (0..100) of the total quantity held by the 100 largest holders. */
    @Column(name = "top100_share", nullable = false, precision = 9, scale = 4)
    private BigDecimal top100Share;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getHoldersCount() { return holdersCount; }
    public void setHoldersCount(int holdersCount) { this.holdersCount = holdersCount; }
    public BigDecimal getTop10Share() { return top10Share; }
    public void setTop10Share(BigDecimal top10Share) { this.top10Share = top10Share; }
    public BigDecimal getTop100Share() { return top100Share; }
    public void setTop100Share(BigDecimal top100Share) { this.top100Share = top100Share; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
