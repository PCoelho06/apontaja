package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.UUID;

/** Vue côté client d'une réservation créée : jamais d'identifiant de profil ni de compte. */
public record OnlineBookingResult(UUID appointmentId, UUID salonId, UUID serviceId, String serviceName,
        UUID resourceId, String resourceName, Instant startAt, Instant endAt, String status, int priceCents,
        int durationMinutes, Instant createdAt) {
}
