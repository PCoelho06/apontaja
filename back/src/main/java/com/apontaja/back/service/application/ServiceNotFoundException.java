package com.apontaja.back.service.application;

public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException() {
        super("Prestation introuvable pour ce salon.");
    }
}
