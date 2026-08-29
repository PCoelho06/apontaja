package com.apontaja.back.account.application;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        // Message volontairement générique (pas d'écho de l'email en clair
        // dans un message d'exception qui pourrait finir dans des logs non
        // maîtrisés).
        super("Un compte existe déjà pour cet email.");
    }
}
