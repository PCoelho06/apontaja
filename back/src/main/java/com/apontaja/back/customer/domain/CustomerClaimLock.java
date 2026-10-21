package com.apontaja.back.customer.domain;

import java.util.UUID;

/** Sérialise les rattachements concurrents d'un même compte (verrou levé à la fin de la transaction). */
public interface CustomerClaimLock {

    void lockAccount(UUID accountId);
}
