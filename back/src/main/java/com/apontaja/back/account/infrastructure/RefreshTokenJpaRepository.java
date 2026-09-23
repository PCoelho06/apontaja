package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT r FROM RefreshToken r WHERE r.accountId = :accountId AND r.revokedAt IS NULL")
    List<RefreshToken> findActiveByAccountId(@Param("accountId") UUID accountId);
}
