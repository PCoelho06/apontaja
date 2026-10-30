package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié dans la transaction d'une réservation, d'une annulation par le client ou d'une décision du
 * staff ; l'email part après la validation ({@link ClientAppointmentEmailListener}). Porte tous les faits utiles :
 * l'écouteur n'a qu'à retrouver les coordonnées du destinataire.
 *
 * <p>
 * Destinataire : {@code accountId} (actions du client, compte connu) ou, s'il est nul, {@code customerProfileId}
 * (actions du staff : le profil peut être rattaché à un compte ou porter son propre email).
 */
record ClientAppointmentNotification(Kind kind, UUID accountId, UUID appointmentId, String salonName,
        String salonAddress, String salonPostalCode, String salonCity, String timezone, String serviceName,
        String resourceName, Instant startAt, Instant endAt, int priceCents, int durationMinutes,
        int cancellationDeadlineHours, UUID customerProfileId) {

    /** Notification adressée à un compte (réservation et annulation par le client). */
    ClientAppointmentNotification(Kind kind, UUID accountId, UUID appointmentId, String salonName,
            String salonAddress, String salonPostalCode, String salonCity, String timezone, String serviceName,
            String resourceName, Instant startAt, Instant endAt, int priceCents, int durationMinutes,
            int cancellationDeadlineHours) {
        this(kind, accountId, appointmentId, salonName, salonAddress, salonPostalCode, salonCity, timezone,
                serviceName, resourceName, startAt, endAt, priceCents, durationMinutes, cancellationDeadlineHours,
                null);
    }

    enum Kind {
        REQUEST_RECEIVED, CONFIRMED, CANCELLED, REQUEST_DECLINED, CANCELLED_BY_SALON, RESCHEDULED_BY_SALON,
        REQUEST_EXPIRED
    }
}
