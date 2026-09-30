package com.tokentracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.TokenTopHolder;

@Repository
public interface TokenTopHolderRepository extends JpaRepository<TokenTopHolder, Long> {

    List<TokenTopHolder> findByAssetIdAndDateOrderByRankAsc(String assetId, LocalDate date);

    /** Latest snapshot date of a token, if any. */
    @Query("select max(h.date) from TokenTopHolder h where h.assetId = :assetId")
    Optional<LocalDate> findLatestDate(@Param("assetId") String assetId);

    /** Snapshot date immediately preceding the given one, used to compute balance changes. */
    @Query("select max(h.date) from TokenTopHolder h where h.assetId = :assetId and h.date < :date")
    Optional<LocalDate> findPreviousDate(@Param("assetId") String assetId, @Param("date") LocalDate date);

    /** All snapshot dates of a token, newest first. */
    @Query("select distinct h.date from TokenTopHolder h where h.assetId = :assetId order by h.date desc")
    List<LocalDate> findDates(@Param("assetId") String assetId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TokenTopHolder h where h.assetId = :assetId and h.date = :date")
    int deleteByAssetIdAndDate(@Param("assetId") String assetId, @Param("date") LocalDate date);
}
