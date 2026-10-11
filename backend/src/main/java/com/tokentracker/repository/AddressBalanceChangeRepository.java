package com.tokentracker.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.AddressBalanceChange;

@Repository
public interface AddressBalanceChangeRepository extends JpaRepository<AddressBalanceChange, Long> {

    List<AddressBalanceChange> findByWatchedAddressIdOrderByDetectedAtDescIdDesc(Long watchedAddressId, Pageable pageable);

    /** Variations still waiting for their Telegram message, oldest first. */
    List<AddressBalanceChange> findByWatchedAddressIdAndNotifiedFalseAndDetectedAtAfterOrderByDetectedAtAscIdAsc(
            Long watchedAddressId, Instant after);

    @Modifying
    @Query("delete from AddressBalanceChange c where c.watchedAddressId = :watchedAddressId")
    void deleteByWatchedAddressId(@Param("watchedAddressId") Long watchedAddressId);
}
