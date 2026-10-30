package com.apontaja.back.appointment.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Rendez-vous vu par son client. Jamais d'identifiant de profil ou de compte,
 * ni de motif ou d'auteur d'annulation.
 */
public record ClientAppointmentResponse(UUID appointmentId, SalonRef salon, ServiceRef service, ResourceRef resource,
        Instant startAt, Instant endAt, String status, int priceCents, int durationMinutes, boolean cancellable,
        Instant cancellableUntil, Instant cancelledAt, boolean expired) {

    public ClientAppointmentResponse(UUID appointmentId, SalonRef salon, ServiceRef service, ResourceRef resource,
            Instant startAt, Instant endAt, String status, int priceCents, int durationMinutes, boolean cancellable,
            Instant cancellableUntil, Instant cancelledAt) {
        this(appointmentId, salon, service, resource, startAt, endAt, status, priceCents, durationMinutes, cancellable,
                cancellableUntil, cancelledAt, false);
    }

    public record SalonRef(UUID id, String name, String city, String timezone) {
    }

    public record ServiceRef(UUID id, String name) {
    }

    public record ResourceRef(UUID id, String name) {
    }
}
