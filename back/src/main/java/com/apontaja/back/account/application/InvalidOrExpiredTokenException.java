package com.apontaja.back.account.application;

public class InvalidOrExpiredTokenException extends RuntimeException {

    public InvalidOrExpiredTokenException() {
        super("Lien invalide ou expiré.");
    }
}
