package com.apontaja.back.appointment.application;

/** RDV inconnu, supprimé ou n'appartenant pas au compte : même 404 uniforme dans tous les cas. */
public class ClientAppointmentNotFoundException extends RuntimeException {

    public ClientAppointmentNotFoundException() {
        super("Introuvable.");
    }
}
