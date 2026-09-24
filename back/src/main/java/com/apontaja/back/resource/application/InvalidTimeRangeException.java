package com.apontaja.back.resource.application;

/** Entrée temporelle invalide (jour, heure, date, plage, chevauchement). */
public class InvalidTimeRangeException extends RuntimeException {

    public InvalidTimeRangeException(String message) {
        super(message);
    }
}
