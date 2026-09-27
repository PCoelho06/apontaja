package com.apontaja.back.appointment.application;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Client introuvable pour ce salon.");
    }
}
