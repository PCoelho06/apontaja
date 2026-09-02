package com.apontaja.back.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository {

    AccountToken save(AccountToken token);

    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** "Actif" = pas encore utilisé (used_at IS NULL) ; l'expiration se vérifie en mémoire. */
    List<AccountToken> findActiveByAccountIdAndType(UUID accountId, AccountTokenType type);
}
