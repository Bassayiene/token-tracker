package com.tokentracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.TokenQuantity;

@Repository
public interface TokenQuantityRepository extends JpaRepository<TokenQuantity, Long> {

    Optional<TokenQuantity> findByAssetIdAndDate(String assetId, LocalDate date);

    List<TokenQuantity> findByAssetIdAndDateBetweenOrderByDateAsc(String assetId, LocalDate from, LocalDate to);

    Optional<TokenQuantity> findFirstByAssetIdOrderByDateDesc(String assetId);

    boolean existsByAssetIdAndDate(String assetId, LocalDate date);
}
