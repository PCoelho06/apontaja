package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.UUID;

/** Plage occupée d'une ressource par un rendez-vous actif. */
public record BusyInterval(UUID resourceId, Instant startAt, Instant endAt) {
}
