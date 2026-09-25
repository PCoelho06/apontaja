package com.apontaja.back.resource.application;

/**
 * Le fuseau enregistré sur le salon n'est pas un identifiant IANA valide
 * (Salon.timezone est du texte libre à la création).
 */
public class InvalidSalonTimezoneException extends RuntimeException {

    public InvalidSalonTimezoneException() {
        super("Le fuseau horaire du salon est invalide.");
    }
}
