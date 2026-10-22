package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.UUID;

public interface BookingQuotaRepository {

    /**
     * Nombre de RDV à venir (début strictement après {@code now}), ni annulés ni supprimés, ni terminés, ni
     * « absent » (statuts SCHEDULED et CONFIRMED) du client dans ce salon.
     */
    int countUpcomingActive(UUID salonId, UUID customerProfileId, Instant now);
}
