package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Règle d'annulation par le client (pure, sans accès base). {@code SCHEDULED} (demande non validée) : annulable
 * jusqu'au début du RDV (exclu). {@code CONFIRMED} : annulable jusqu'à {@code début - délai} (inclus), le délai
 * étant le réglage courant du salon. Tout autre statut n'est pas annulable.
 */
public final class CancellationPolicy {

    public enum Verdict {
        ALLOWED, NOT_ALLOWED, DEADLINE_PASSED
    }

    private CancellationPolicy() {
    }

    public static Verdict evaluate(AppointmentStatus status, Instant startAt, int deadlineHours, Instant now) {
        return switch (status) {
            case SCHEDULED -> now.isBefore(startAt) ? Verdict.ALLOWED : Verdict.NOT_ALLOWED;
            case CONFIRMED -> now.isAfter(deadline(startAt, deadlineHours)) ? Verdict.DEADLINE_PASSED
                    : Verdict.ALLOWED;
            default -> Verdict.NOT_ALLOWED;
        };
    }

    /** Dernier instant d'annulation, ou {@code null} si le statut n'est pas annulable. */
    public static Instant cancellableUntil(AppointmentStatus status, Instant startAt, int deadlineHours) {
        return switch (status) {
            case SCHEDULED -> startAt;
            case CONFIRMED -> deadline(startAt, deadlineHours);
            default -> null;
        };
    }

    private static Instant deadline(Instant startAt, int deadlineHours) {
        return startAt.minus(deadlineHours, ChronoUnit.HOURS);
    }
}
