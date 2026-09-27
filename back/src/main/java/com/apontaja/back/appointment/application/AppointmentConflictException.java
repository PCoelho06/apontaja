package com.apontaja.back.appointment.application;

public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException() {
        super("Ce créneau vient d'être réservé pour cette ressource, réessayez.");
    }
}
