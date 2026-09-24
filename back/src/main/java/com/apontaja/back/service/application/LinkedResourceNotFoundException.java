package com.apontaja.back.service.application;

public class LinkedResourceNotFoundException extends RuntimeException {

    public LinkedResourceNotFoundException() {
        super("Ressource introuvable pour ce salon.");
    }
}
