package com.apontaja.back.resource.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Moteur de disponibilité : fonctions pures, sans Spring, JPA ni horloge.
 *
 * <p>
 * <b>Règles</b> (le créneau est un intervalle d'instants [début, fin)) :
 * <ul>
 * <li>le créneau doit tenir ENTIÈREMENT (début ET fin) dans une plage
 * d'ouverture du salon, sans traverser une pause ni un jour sans horaire ;</li>
 * <li>si la ressource a ses propres horaires, il doit aussi tenir dans l'un
 * d'eux (elle reste bornée par le salon) ; sans horaires propres, elle suit le
 * salon. Dès qu'une ressource a au moins une plage, un jour sans plage = fermé ;
 * </li>
 * <li>aucune fermeture (salon ou ressource) ne doit recouvrir le créneau, même
 * partiellement (bornes adjacentes = pas de recouvrement) ;</li>
 * <li>un salon sans aucun horaire n'est jamais ouvert.</li>
 * </ul>
 *
 * <p>
 * <b>Fuseau et DST</b> : les plages hebdomadaires sont en heure locale du
 * salon et converties en instants pour chaque date locale concernée. Une heure
 * locale est résolue vers le PREMIER instant dont l'heure locale est &gt;= à
 * celle demandée : heure inexistante (saut de printemps) = instant de la
 * transition ; heure ambiguë (retour d'automne) = première occurrence. Une
 * plage a donc la durée réelle que lui donne le calendrier ce jour-là (par
 * exemple 00:00-06:00 dure 5 h au passage à l'heure d'été, 7 h à l'heure
 * d'hiver). Une plage ne traverse jamais minuit : un créneau à cheval sur deux
 * jours n'est donc jamais couvert (limite connue, cf. plages jusqu'à 23:59).
 *
 * <p>
 * Ne s'occupe PAS des autres rendez-vous : le conflit avec un RDV existant est
 * contrôlé par le domaine appointment (et par la contrainte d'exclusion
 * PostgreSQL).
 */
public final class AvailabilityEngine {

    private AvailabilityEngine() {
    }

    public static AvailabilityResult check(AvailabilityInput input) {
        Objects.requireNonNull(input, "input");
        List<AvailabilityViolation> violations = new ArrayList<>();

        if (!isCoveredByHours(input.slot(), input.zone(), input.salonHours())) {
            violations.add(AvailabilityViolation.OUTSIDE_SALON_HOURS);
        }
        if (!input.resourceHours().isEmpty() && !isCoveredByHours(input.slot(), input.zone(),
                input.resourceHours())) {
            violations.add(AvailabilityViolation.OUTSIDE_RESOURCE_HOURS);
        }
        if (overlapsAny(input.slot(), input.salonClosures())) {
            violations.add(AvailabilityViolation.SALON_CLOSED);
        }
        if (overlapsAny(input.slot(), input.resourceClosures())) {
            violations.add(AvailabilityViolation.RESOURCE_CLOSED);
        }
        return new AvailabilityResult(violations);
    }

    /** Vrai si le créneau tient d'un seul tenant dans les plages (contiguës fusionnées). */
    public static boolean isCoveredByHours(TimeInterval slot, ZoneId zone, List<WeeklyWindow> hours) {
        if (hours.isEmpty()) {
            return false;
        }
        LocalDate firstDate = slot.start().atZone(zone).toLocalDate();
        LocalDate lastDate = slot.end().minusNanos(1).atZone(zone).toLocalDate();

        List<TimeInterval> windows = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            windows.addAll(windowsOn(zone, hours, date));
        }
        return merge(windows).stream().anyMatch(window -> window.contains(slot));
    }

    /**
     * Plages d'ouverture d'une date locale, en instants, triées. Une plage
     * réduite à rien par un saut d'heure est omise.
     */
    public static List<TimeInterval> windowsOn(ZoneId zone, List<WeeklyWindow> hours, LocalDate date) {
        List<TimeInterval> windows = new ArrayList<>();
        for (WeeklyWindow window : hours) {
            if (window.dayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            Instant start = resolveLocal(date.atTime(window.start()), zone);
            Instant end = resolveLocal(date.atTime(window.end()), zone);
            if (end.isAfter(start)) {
                windows.add(new TimeInterval(start, end));
            }
        }
        windows.sort(Comparator.comparing(TimeInterval::start));
        return windows;
    }

    /** Premier instant dont l'heure locale est &gt;= {@code local} dans ce fuseau. */
    public static Instant resolveLocal(LocalDateTime local, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        if (rules.getValidOffsets(local).size() == 1) {
            return local.toInstant(rules.getValidOffsets(local).get(0));
        }
        ZoneOffsetTransition transition = rules.getTransition(local);
        if (transition.isOverlap()) {
            return local.toInstant(transition.getOffsetBefore());
        }
        return transition.getInstant();
    }

    private static List<TimeInterval> merge(List<TimeInterval> windows) {
        List<TimeInterval> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparing(TimeInterval::start));
        List<TimeInterval> merged = new ArrayList<>();
        for (TimeInterval window : sorted) {
            if (merged.isEmpty() || window.start().isAfter(merged.get(merged.size() - 1).end())) {
                merged.add(window);
            } else {
                TimeInterval last = merged.remove(merged.size() - 1);
                Instant end = window.end().isAfter(last.end()) ? window.end() : last.end();
                merged.add(new TimeInterval(last.start(), end));
            }
        }
        return merged;
    }

    private static boolean overlapsAny(TimeInterval slot, List<TimeInterval> closures) {
        return closures.stream().anyMatch(slot::overlaps);
    }
}
