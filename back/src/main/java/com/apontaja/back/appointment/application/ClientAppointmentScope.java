package com.apontaja.back.appointment.application;

import java.util.Locale;

/** Filtre de la liste « mes rendez-vous » : à venir ou passés (dont annulés). */
public enum ClientAppointmentScope {
    UPCOMING, PAST;

    /** @throws InvalidClientAppointmentQueryException valeur absente ou inconnue */
    public static ClientAppointmentScope parse(String raw) {
        if (raw != null) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.equals("upcoming")) {
                return UPCOMING;
            }
            if (value.equals("past")) {
                return PAST;
            }
        }
        throw new InvalidClientAppointmentQueryException("Le paramètre scope doit valoir « upcoming » ou « past ».");
    }
}
