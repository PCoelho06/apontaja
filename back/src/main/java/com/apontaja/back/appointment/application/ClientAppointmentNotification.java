package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié dans la transaction d'une réservation ou d'une annulation par le client ; l'email part après la
 * validation ({@link ClientAppointmentEmailListener}). Porte tous les faits utiles : l'écouteur n'a qu'à retrouver
 * les coordonnées du compte.
 */
record ClientAppointmentNotification(Kind kind, UUID accountId, UUID appointmentId, String salonName,
        String salonAddress, String salonPostalCode, String salonCity, String timezone, String serviceName,
        String resourceName, Instant startAt, Instant endAt, int priceCents, int durationMinutes,
        int cancellationDeadlineHours) {

    enum Kind {
        REQUEST_RECEIVED, CONFIRMED, CANCELLED
    }
}
