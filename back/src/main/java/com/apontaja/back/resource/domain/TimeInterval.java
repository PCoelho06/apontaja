package com.apontaja.back.resource.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Intervalle d'instants semi-ouvert [start, end). Deux intervalles qui se
 * touchent (end de l'un = start de l'autre) ne se chevauchent pas.
 */
public record TimeInterval(Instant start, Instant end) {

    public TimeInterval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    /** Vrai si {@code other} tient entièrement dans cet intervalle (bornes incluses). */
    public boolean contains(TimeInterval other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }
}
