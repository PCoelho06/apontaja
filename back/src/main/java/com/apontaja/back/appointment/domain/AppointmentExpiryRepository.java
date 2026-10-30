package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.List;

/** Lecture des demandes échues (le service relit chacune sous verrou avant d'agir). */
public interface AppointmentExpiryRepository {

    /**
     * Demandes {@code SCHEDULED} non supprimées dont l'échéance ({@link RequestExpiryPolicy#expiresAt}) est
     * atteinte à {@code now}, de la plus ancienne à la plus récente, au plus {@code limit}.
     */
    List<PendingRequest> findDue(Instant now, int limit);
}
