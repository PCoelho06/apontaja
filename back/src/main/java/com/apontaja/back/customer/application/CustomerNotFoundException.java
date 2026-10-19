package com.apontaja.back.customer.application;

/** Client inconnu, supprimé, ou appartenant à un autre salon. */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Client introuvable.");
    }
}
