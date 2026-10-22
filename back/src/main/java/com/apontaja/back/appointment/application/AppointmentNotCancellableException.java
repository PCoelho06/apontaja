package com.apontaja.back.appointment.application;

/** Le statut du RDV (ou son début, pour une demande non validée déjà commencée) interdit l'annulation (409). */
public class AppointmentNotCancellableException extends RuntimeException {

    public AppointmentNotCancellableException() {
        super("Ce rendez-vous ne peut plus être annulé.");
    }
}
