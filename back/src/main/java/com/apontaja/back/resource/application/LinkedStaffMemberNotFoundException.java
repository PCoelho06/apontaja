package com.apontaja.back.resource.application;

public class LinkedStaffMemberNotFoundException extends RuntimeException {

    public LinkedStaffMemberNotFoundException() {
        super("Membre de l'équipe introuvable pour ce salon.");
    }
}
