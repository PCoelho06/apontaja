package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des RDV d'un compte client. Le rattachement passe par {@code customer_profile.account_id} (SQL natif :
 * {@code appointment} ne lit pas les entités de {@code customer}).
 */
public interface ClientAppointmentRepository {

    /**
     * {@code upcoming} : SCHEDULED/CONFIRMED qui ne sont pas terminés à {@code now}, du plus proche au plus lointain.
     * Sinon : tous les autres (terminés, absents, annulés, demandes passées), du plus récent au plus ancien.
     * {@code page} commence à 0.
     */
    ClientAppointmentRows findForAccount(UUID accountId, boolean upcoming, Instant now, int page, int size);

    /** Vide si le RDV n'existe pas, est supprimé ou n'appartient pas au compte. */
    Optional<ClientAppointmentRow> findOwned(UUID accountId, UUID appointmentId);
}
