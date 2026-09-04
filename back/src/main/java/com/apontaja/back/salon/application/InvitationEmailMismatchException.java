package com.apontaja.back.salon.application;

public class InvitationEmailMismatchException extends RuntimeException {

    public InvitationEmailMismatchException() {
        super("Cette invitation ne correspond pas à l'email de votre compte.");
    }
}
