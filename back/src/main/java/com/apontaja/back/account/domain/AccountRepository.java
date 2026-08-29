package com.apontaja.back.account.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Port défini par le domaine, implémenté par {@code infrastructure}
 * (règle de dépendance : infrastructure -> domain, jamais l'inverse).
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    /** Compte "vivant" (non soft-deleted) par email — unicité applicative + login. */
    Optional<Account> findAliveByEmail(String email);

    boolean existsAliveByEmail(String email);
}
