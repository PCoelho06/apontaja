package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Un rendez-vous vu par son client. {@code cancellable} et
 * {@code cancellableUntil} reflètent le réglage courant du salon ;
 * {@code cancellableUntil} est nul si le statut n'est pas annulable. Ni motif
 * ni auteur d'annulation.
 */
public record ClientAppointment(UUID appointmentId, SalonRef salon, ServiceRef service, ResourceRef resource,
        Instant startAt, Instant endAt, String status, int priceCents, int durationMinutes, boolean cancellable,
        Instant cancellableUntil, Instant cancelledAt, boolean expired) {

    public ClientAppointment(UUID appointmentId, SalonRef salon, ServiceRef service, ResourceRef resource,
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
