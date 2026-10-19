package com.apontaja.back.customer.application;

/** Le profil est réclamé par un compte ou rattaché à un autre salon : un salon ne peut pas l'anonymiser. */
public class CustomerSharedException extends RuntimeException {

    public CustomerSharedException() {
        super("Ce profil est rattaché à un compte ou à un autre salon : anonymisation impossible.");
    }
}
