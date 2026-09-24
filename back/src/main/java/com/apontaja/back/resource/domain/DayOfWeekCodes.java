package com.apontaja.back.resource.domain;

import java.time.DayOfWeek;

/**
 * Seul point de conversion entre {@link DayOfWeek} (1 = lundi … 7 = dimanche)
 * et la colonne {@code schedule.day_of_week} du schéma (0 = dimanche …
 * 6 = samedi). Ne jamais convertir ailleurs.
 */
public final class DayOfWeekCodes {

    private DayOfWeekCodes() {
    }

    public static short toCode(DayOfWeek dayOfWeek) {
        return (short) (dayOfWeek.getValue() % 7);
    }

    public static DayOfWeek fromCode(int code) {
        if (code < 0 || code > 6) {
            throw new IllegalArgumentException("Code de jour invalide : " + code);
        }
        return code == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(code);
    }
}
