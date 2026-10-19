package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.UUID;

/** Ligne d'historique d'un client (projection JPQL). */
public record AppointmentHistoryRow(UUID appointmentId, Instant startAt, Instant endAt, AppointmentStatus status,
        UUID serviceId, int priceAtBookingCents, int durationAtBookingMinutes) {
}
