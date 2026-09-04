package com.apontaja.back.salon.application;

public class StaffMembershipNotFoundException extends RuntimeException {

    public StaffMembershipNotFoundException() {
        super("Membre du staff introuvable pour ce salon.");
    }
}
