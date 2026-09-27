package com.apontaja.back.appointment.application;

public class ServiceResourceNotAssociatedException extends RuntimeException {

    public ServiceResourceNotAssociatedException() {
        super("Cette ressource ne réalise pas cette prestation dans ce salon.");
    }
}
