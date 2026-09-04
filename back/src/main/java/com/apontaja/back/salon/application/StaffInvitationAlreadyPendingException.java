package com.apontaja.back.salon.application;

public class StaffInvitationAlreadyPendingException extends RuntimeException {

    public StaffInvitationAlreadyPendingException() {
        super("Une invitation est déjà en attente pour cet email sur ce salon.");
    }
}
