package com.apontaja.back.customer.application;

/** Première réservation dans un salon : le client doit accepter de partager nom et coordonnées avec lui. */
public class SharingConsentRequiredException extends RuntimeException {

    public SharingConsentRequiredException() {
        super("Vous devez accepter de partager votre nom et vos coordonnées avec le salon pour réserver.");
    }
}
