package com.tokentracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tokentracker.domain.Token;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByAssetId(String assetId);

    boolean existsByAssetId(String assetId);

    List<Token> findAllByOrderByIdAsc();
}
