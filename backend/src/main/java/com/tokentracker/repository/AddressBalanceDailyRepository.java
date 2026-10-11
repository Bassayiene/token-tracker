package com.tokentracker.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.AddressBalanceDaily;
import com.tokentracker.service.dto.AddressHistoryAssetDTO;

@Repository
public interface AddressBalanceDailyRepository extends JpaRepository<AddressBalanceDaily, Long> {

    /** Every asset row of one day, to create or update them at each check. */
    List<AddressBalanceDaily> findByWatchedAddressIdAndDate(Long watchedAddressId, LocalDate date);

    List<AddressBalanceDaily> findByWatchedAddressIdAndAssetIdAndDateBetweenOrderByDateAsc(
            Long watchedAddressId, String assetId, LocalDate from, LocalDate to);

    /** Every asset row of a period, oldest day first. */
    List<AddressBalanceDaily> findByWatchedAddressIdAndDateBetweenOrderByDateAscIdAsc(
            Long watchedAddressId, LocalDate from, LocalDate to);

    /** The row of the first tracked day of every asset of the address. */
    @Query("""
            select d from AddressBalanceDaily d
            where d.watchedAddressId = :watchedAddressId
              and d.date = (select min(f.date) from AddressBalanceDaily f
                            where f.watchedAddressId = d.watchedAddressId and f.assetId = d.assetId)
            """)
    List<AddressBalanceDaily> findFirstDays(@Param("watchedAddressId") Long watchedAddressId);

    /** Every asset that has a history for the address, including those no longer held. */
    @Query("""
            select new com.tokentracker.service.dto.AddressHistoryAssetDTO(d.assetId, max(d.assetName), min(d.date), max(d.date))
            from AddressBalanceDaily d
            where d.watchedAddressId = :watchedAddressId
            group by d.assetId
            """)
    List<AddressHistoryAssetDTO> findAssets(@Param("watchedAddressId") Long watchedAddressId);

    @Modifying
    @Query("delete from AddressBalanceDaily d where d.watchedAddressId = :watchedAddressId")
    void deleteByWatchedAddressId(@Param("watchedAddressId") Long watchedAddressId);
}
