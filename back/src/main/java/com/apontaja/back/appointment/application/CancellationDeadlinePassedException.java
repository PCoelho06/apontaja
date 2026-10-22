package com.apontaja.back.appointment.application;

/** Rendez-vous confirmé dont le délai d'annulation du salon est dépassé (409). */
public class CancellationDeadlinePassedException extends RuntimeException {

    public CancellationDeadlinePassedException(int deadlineHours) {
        super("Ce rendez-vous confirmé ne peut plus être annulé en ligne (délai : " + deadlineHours
                + " h avant le début). Contactez le salon.");
    }
}
