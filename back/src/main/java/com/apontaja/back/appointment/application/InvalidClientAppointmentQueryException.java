package com.apontaja.back.appointment.application;

/** Paramètres de liste invalides (400). */
public class InvalidClientAppointmentQueryException extends RuntimeException {

    public InvalidClientAppointmentQueryException(String message) {
        super(message);
    }
}
