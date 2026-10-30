package com.apontaja.back.appointment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Règle d'expiration d'une demande de réservation en ligne restée {@code SCHEDULED} (pure, sans accès base).
 *
 * <p>
 * Le salon a au plus {@link #MAX_WAIT} après la création pour répondre ; si le rendez-vous est proche, la limite
 * est ramenée à {@link #LEAD} avant le début, sans jamais laisser moins de {@link #MIN_WAIT} après la création, et
 * jamais au-delà du début. {@code expiresAt = min(création + 48 h, max(début - 24 h, création + 4 h), début)}.
 */
public final class RequestExpiryPolicy {

    public static final Duration MAX_WAIT = Duration.ofHours(48);
    public static final Duration LEAD = Duration.ofHours(24);
    public static final Duration MIN_WAIT = Duration.ofHours(4);

    /** Raison stockée à l'expiration (jamais envoyée au client ; {@code cancelled_by} reste nul). */
    public static final String EXPIRY_REASON = "Pedido expirado sem resposta do salão";

    private RequestExpiryPolicy() {
    }

    public static Instant expiresAt(Instant createdAt, Instant startAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(startAt, "startAt");
        Instant beforeStart = startAt.minus(LEAD);
        Instant floor = createdAt.plus(MIN_WAIT);
        Instant answerBy = beforeStart.isAfter(floor) ? beforeStart : floor;
        return earliest(earliest(createdAt.plus(MAX_WAIT), answerBy), startAt);
    }

    /** Vrai à partir de l'échéance incluse. */
    public static boolean isDue(Instant createdAt, Instant startAt, Instant now) {
        return !now.isBefore(expiresAt(createdAt, startAt));
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
