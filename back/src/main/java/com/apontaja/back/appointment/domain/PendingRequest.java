package com.apontaja.back.appointment.domain;

import java.util.UUID;

/** Demande {@code SCHEDULED} échue, à traiter sous le verrou de son salon. */
public record PendingRequest(UUID appointmentId, UUID salonId) {
}
