package com.apontaja.back.appointment.application;

/**
 * Introuvable côté public : salon inconnu, supprimé ou sans réservation en ligne, prestation ou
 * ressource hors de ce salon. Volontairement sans détail : les cas sont indiscernables.
 */
public class PublicBookingNotFoundException extends RuntimeException {

    public PublicBookingNotFoundException() {
        super("Introuvable.");
    }
}
