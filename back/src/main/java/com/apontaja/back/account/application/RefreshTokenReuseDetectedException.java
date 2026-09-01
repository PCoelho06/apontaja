package com.apontaja.back.account.application;

public class RefreshTokenReuseDetectedException extends RuntimeException {

    public RefreshTokenReuseDetectedException() {
        super("Réutilisation de token détectée, toutes les sessions ont été révoquées.");
    }
}
