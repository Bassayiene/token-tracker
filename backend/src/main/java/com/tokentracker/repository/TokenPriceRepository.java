package com.tokentracker.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.TokenPrice;

@Repository
public interface TokenPriceRepository extends JpaRepository<TokenPrice, Long> {

    /** Prices of a token in [from, to], both bounds included. */
    List<TokenPrice> findByAssetIdAndPriceTimeBetweenOrderByPriceTimeAsc(String assetId, Instant from, Instant to);

    Optional<TokenPrice> findByAssetIdAndPriceTime(String assetId, Instant priceTime);

    /** Most recent stored hour. */
    Optional<TokenPrice> findFirstByAssetIdOrderByPriceTimeDesc(String assetId);

    /** Last known price strictly before the given hour; seeds the carry-forward of a sync run. */
    Optional<TokenPrice> findFirstByAssetIdAndPriceTimeBeforeAndPriceIsNotNullOrderByPriceTimeDesc(
            String assetId, Instant before);

    /** Most recent hour with a real trade. */
    Optional<TokenPrice> findFirstByAssetIdAndHasTradesTrueOrderByPriceTimeDesc(String assetId);

    boolean existsByAssetId(String assetId);
}
