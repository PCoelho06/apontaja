package com.apontaja.back.resource.application;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Ressource introuvable pour ce salon.");
    }
}
