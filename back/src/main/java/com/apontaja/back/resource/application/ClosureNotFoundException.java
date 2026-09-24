package com.apontaja.back.resource.application;

public class ClosureNotFoundException extends RuntimeException {

    public ClosureNotFoundException() {
        super("Fermeture introuvable.");
    }
}
