package com.apontaja.back.resource.application;

public class InvalidResourceStaffLinkException extends RuntimeException {

    public InvalidResourceStaffLinkException() {
        super("Seule une ressource de type EMPLOYEE peut être liée à un membre de l'équipe.");
    }
}
