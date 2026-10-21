package com.apontaja.back.customer.application;

/** Un profil réclamé par un compte peut être conservé lors d'une fusion, mais jamais absorbé. */
public class CustomerClaimedException extends RuntimeException {

    public CustomerClaimedException() {
        super("Ce profil est rattaché à un compte client : il peut être conservé, pas fusionné dans un autre.");
    }
}
