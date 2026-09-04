package com.apontaja.back.salon.application;

public class InvalidOrExpiredInvitationException extends RuntimeException {

    public InvalidOrExpiredInvitationException() {
        super("Invitation invalide ou expirée.");
    }
}
