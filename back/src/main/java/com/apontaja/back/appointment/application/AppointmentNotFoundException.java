package com.apontaja.back.appointment.application;

public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException() {
        super("Rendez-vous introuvable pour ce salon.");
    }
}
