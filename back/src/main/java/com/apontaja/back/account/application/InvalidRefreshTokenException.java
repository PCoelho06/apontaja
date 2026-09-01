package com.apontaja.back.account.application;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token invalide ou expiré.");
    }
}
