package com.apontaja.back.resource.application;

import java.time.Instant;

/**
 * Horaires et fermetures d'une ressource (et de son salon) chargés une fois pour une fenêtre donnée,
 * pour tester rapidement de nombreux créneaux candidats sans retourner en base. Comme
 * {@code AvailabilityService.check}, ne tient PAS compte des autres rendez-vous.
 */
@FunctionalInterface
public interface AvailabilityPlan {

    /** Vrai si le créneau [start, end) respecte horaires et fermetures (mêmes règles que {@code check}). */
    boolean isAvailable(Instant start, Instant end);
}
