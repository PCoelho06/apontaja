package com.apontaja.back.customer.application;

/** Réserver en ligne exige un compte vivant dont l'email est vérifié (pas d'invité). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Vérifiez votre adresse email avant de réserver.");
    }
}
