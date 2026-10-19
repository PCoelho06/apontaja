package com.apontaja.back.customer.application;

public class CustomerAnonymizedException extends RuntimeException {

    public CustomerAnonymizedException() {
        super("Ce client est anonymisé : il n'est plus modifiable.");
    }
}
