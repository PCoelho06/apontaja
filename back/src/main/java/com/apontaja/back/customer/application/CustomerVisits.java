package com.apontaja.back.customer.application;

import java.time.Instant;

/** Dates de visite d'un client dans un salon, dérivées de ses rendez-vous (jamais stockées). */
public record CustomerVisits(Instant lastVisitAt, Instant nextVisitAt) {

    public static final CustomerVisits NONE = new CustomerVisits(null, null);
}
