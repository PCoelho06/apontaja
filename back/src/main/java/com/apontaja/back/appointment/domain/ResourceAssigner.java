package com.apontaja.back.appointment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Choisit la ressource d'une réservation « sans préférence » : la moins chargée du jour (minutes de RDV actifs
 * recouvrant la journée), puis la plus ancienne, puis le plus petit identifiant (résultat déterministe).
 * Fonction pure, sans Spring ni horloge.
 */
public final class ResourceAssigner {

    private static final Comparator<Candidate> OLDEST_FIRST = Comparator.comparing(Candidate::createdAt)
            .thenComparing(Candidate::resourceId);

    private ResourceAssigner() {
    }

    public record Candidate(UUID resourceId, Instant createdAt) {
    }

    /**
     * @param busy plages occupées (toutes ressources confondues, les ressources hors candidats sont ignorées)
     * @param dayStart début (inclus) du jour local ; {@code dayEnd} sa fin (exclue)
     */
    public static UUID choose(List<Candidate> candidates, List<BusyInterval> busy, Instant dayStart,
            Instant dayEnd) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Aucune ressource candidate.");
        }
        Map<UUID, Long> loadSeconds = busy.stream().collect(Collectors.groupingBy(BusyInterval::resourceId,
                Collectors.summingLong(interval -> overlapSeconds(interval, dayStart, dayEnd))));
        Function<Candidate, Long> load = candidate -> loadSeconds.getOrDefault(candidate.resourceId(), 0L);
        return candidates.stream().min(Comparator.comparing(load).thenComparing(OLDEST_FIRST)).orElseThrow()
                .resourceId();
    }

    private static long overlapSeconds(BusyInterval interval, Instant dayStart, Instant dayEnd) {
        Instant from = interval.startAt().isAfter(dayStart) ? interval.startAt() : dayStart;
        Instant to = interval.endAt().isBefore(dayEnd) ? interval.endAt() : dayEnd;
        return to.isAfter(from) ? Duration.between(from, to).getSeconds() : 0L;
    }
}
