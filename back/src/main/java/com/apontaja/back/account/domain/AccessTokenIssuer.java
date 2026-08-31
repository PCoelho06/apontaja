package com.apontaja.back.account.domain;

import java.util.UUID;

/** Port défini par le domaine, implémenté par infrastructure (JWT/HS256). */
public interface AccessTokenIssuer {
    String issue(UUID accountId);
}
