package com.apontaja.back.resource.application;

public class StaffMemberAlreadyLinkedException extends RuntimeException {

    public StaffMemberAlreadyLinkedException() {
        super("Ce membre de l'équipe est déjà lié à une autre ressource.");
    }
}
