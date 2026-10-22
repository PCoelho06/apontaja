package com.apontaja.back.appointment.application;

/** Le début demandé n'est (plus) un créneau libre : pris, hors grille, hors préavis ou hors horizon. */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException() {
        super("Ce créneau n'est plus disponible, choisissez-en un autre.");
    }
}
