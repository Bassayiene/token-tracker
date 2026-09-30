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
 * One of the largest holders of a token on a given day (rank 1 = largest balance).
 */
@Entity
@Table(name = "token_top_holder")
public class TokenTopHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 64)
    private String assetId;

    /** UTC day of the snapshot. */
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false, length = 64)
    private String address;

    /** Raw balance (divide by 10^decimals to display). */
    @Column(nullable = false)
    private long balance;

    /** Block height at which the distribution was read. */
    @Column(nullable = false)
    private int height;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public Long getId() { return id; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
