package com.tokentracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.AddressBalance;

@Repository
public interface AddressBalanceRepository extends JpaRepository<AddressBalance, Long> {

    List<AddressBalance> findByWatchedAddressId(Long watchedAddressId);

    long countByWatchedAddressId(Long watchedAddressId);

    @Modifying
    @Query("delete from AddressBalance b where b.watchedAddressId = :watchedAddressId")
    void deleteByWatchedAddressId(@Param("watchedAddressId") Long watchedAddressId);
}
