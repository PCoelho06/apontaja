package com.apontaja.back.customer.application;

public class ContactRequiredException extends RuntimeException {

    public ContactRequiredException() {
        super("Un email ou un numéro de téléphone est requis.");
    }
}
