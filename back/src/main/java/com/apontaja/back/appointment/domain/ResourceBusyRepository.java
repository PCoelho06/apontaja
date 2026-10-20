package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResourceBusyRepository {

    /**
     * Plages des rendez-vous actifs (ni annulés ni supprimés, même définition que la colonne
     * {@code is_active} de {@code appointment_resource}) des ressources données qui recouvrent
     * [from, to), triées par début. Liste vide si {@code resourceIds} est vide.
     */
    List<BusyInterval> findActiveIntervals(Collection<UUID> resourceIds, Instant from, Instant to);
}
