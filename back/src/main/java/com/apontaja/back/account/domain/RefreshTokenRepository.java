package com.apontaja.back.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port minimal pour cette tranche (entités + persistence). La révocation
 * en cascade sur détection de réutilisation (rotation) arrive tranche 5 —
 * pas anticipée ici pour éviter du code mort.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findActiveByAccountId(UUID accountId);
}
