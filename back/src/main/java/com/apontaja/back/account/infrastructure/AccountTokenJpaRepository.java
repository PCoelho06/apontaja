package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AccountTokenJpaRepository extends JpaRepository<AccountToken, UUID> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    @Query("SELECT t FROM AccountToken t WHERE t.accountId = :accountId AND t.type = :type AND t.usedAt IS NULL")
    List<AccountToken> findActiveByAccountIdAndType(
            @Param("accountId") UUID accountId, @Param("type") AccountTokenType type);
}
