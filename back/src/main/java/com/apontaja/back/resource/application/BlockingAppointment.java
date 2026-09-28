package com.apontaja.back.resource.application;

import java.time.Instant;
import java.util.UUID;

/** RDV actif (SCHEDULED/CONFIRMED) qui empêche de poser une fermeture. */
public record BlockingAppointment(UUID appointmentId, UUID resourceId, Instant startAt, Instant endAt) {
}
