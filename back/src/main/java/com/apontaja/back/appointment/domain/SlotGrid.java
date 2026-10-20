package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Grille de débuts de créneaux : fonctions pures, sans Spring, JPA ni horloge.
 *
 * <p>
 * Pour chaque date locale du salon, la grille démarre à minuit local et avance par pas de
 * {@code granularityMinutes} <b>en instants</b> jusqu'à minuit local suivant. Les changements d'heure
 * sont donc naturels : une heure inexistante n'a aucun candidat, une heure ambiguë en a deux séries
 * (une par occurrence), un jour dure 23, 24 ou 25 h. La granularité doit diviser 60.
 */
public final class SlotGrid {

    private SlotGrid() {
    }

    /**
     * Débuts candidats, croissants, des dates {@code firstDate..lastDate} (incluses) dans le fuseau,
     * avec {@code earliest <= début < latestExclusive}.
     */
    public static List<Instant> candidateStarts(ZoneId zone, LocalDate firstDate, LocalDate lastDate,
            int granularityMinutes, Instant earliest, Instant latestExclusive) {
        if (granularityMinutes <= 0 || 60 % granularityMinutes != 0) {
            throw new IllegalArgumentException("La granularité doit être un diviseur de 60 : " + granularityMinutes);
        }
        if (lastDate.isBefore(firstDate)) {
            throw new IllegalArgumentException("lastDate doit être >= firstDate");
        }
        List<Instant> starts = new ArrayList<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            Instant dayStart = date.atStartOfDay(zone).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
            if (!dayStart.isBefore(latestExclusive)) {
                break;
            }
            if (!dayEnd.isAfter(earliest)) {
                continue;
            }
            for (Instant t = dayStart; t.isBefore(dayEnd); t = t.plus(granularityMinutes, ChronoUnit.MINUTES)) {
                if (!t.isBefore(earliest) && t.isBefore(latestExclusive)) {
                    starts.add(t);
                }
            }
        }
        return starts;
    }

    /** Vrai si [start, end) ne recouvre aucune plage occupée (bornes adjacentes = libre). */
    public static boolean isFree(Instant start, Instant end, List<BusyInterval> busy) {
        return busy.stream().noneMatch(b -> b.startAt().isBefore(end) && b.endAt().isAfter(start));
    }
}
