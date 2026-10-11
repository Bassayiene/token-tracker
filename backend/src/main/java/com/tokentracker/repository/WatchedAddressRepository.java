package com.tokentracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.WatchedAddress;

@Repository
public interface WatchedAddressRepository extends JpaRepository<WatchedAddress, Long> {

    boolean existsByAddress(String address);

    List<WatchedAddress> findAllByOrderByIdAsc();
}
