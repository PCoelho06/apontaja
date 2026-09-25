package com.apontaja.back.resource.domain;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Tout ce qu'il faut pour décider si un créneau est réservable, sans aucun
 * accès base : le créneau (instants), le fuseau du salon, les horaires du
 * salon, ceux de la ressource (liste vide = la ressource suit le salon), et les
 * fermetures pertinentes (salon, ressource).
 */
public record AvailabilityInput(TimeInterval slot, ZoneId zone, List<WeeklyWindow> salonHours,
        List<WeeklyWindow> resourceHours, List<TimeInterval> salonClosures, List<TimeInterval> resourceClosures) {

    public AvailabilityInput {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(zone, "zone");
        salonHours = List.copyOf(salonHours);
        resourceHours = List.copyOf(resourceHours);
        salonClosures = List.copyOf(salonClosures);
        resourceClosures = List.copyOf(resourceClosures);
    }
}
