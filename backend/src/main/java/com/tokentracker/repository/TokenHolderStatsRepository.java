package com.tokentracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.TokenHolderStats;

@Repository
public interface TokenHolderStatsRepository extends JpaRepository<TokenHolderStats, Long> {

    Optional<TokenHolderStats> findByAssetIdAndDate(String assetId, LocalDate date);

    List<TokenHolderStats> findByAssetIdAndDateBetweenOrderByDateAsc(String assetId, LocalDate from, LocalDate to);

    Optional<TokenHolderStats> findFirstByAssetIdOrderByDateDesc(String assetId);

    boolean existsByAssetIdAndDate(String assetId, LocalDate date);
}
