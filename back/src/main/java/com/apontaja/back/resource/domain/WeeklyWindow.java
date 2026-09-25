package com.apontaja.back.resource.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Plage d'ouverture récurrente en heure LOCALE du salon (jamais un instant).
 * Value object indépendant de l'entité {@link Schedule}.
 */
public record WeeklyWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {

    public WeeklyWindow {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }
    }

    public static WeeklyWindow from(Schedule schedule) {
        return new WeeklyWindow(schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime());
    }
}
