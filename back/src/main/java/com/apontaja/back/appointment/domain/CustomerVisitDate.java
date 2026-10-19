package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.UUID;

/** Résultat d'agrégation (JPQL) : un début de RDV par client. */
public record CustomerVisitDate(UUID customerProfileId, Instant startAt) {
}
