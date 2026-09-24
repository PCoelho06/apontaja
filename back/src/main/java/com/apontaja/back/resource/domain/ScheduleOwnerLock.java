package com.apontaja.back.resource.domain;

import java.util.UUID;

/**
 * Verrou transactionnel par propriétaire d'horaires (salon ou ressource) :
 * sérialise les remplacements concurrents, faute de contrainte d'exclusion en
 * base sur schedule.
 */
public interface ScheduleOwnerLock {

    void lock(UUID ownerId);
}
