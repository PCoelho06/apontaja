package com.apontaja.back.appointment.application;

public class InvalidAppointmentTransitionException extends RuntimeException {

    public InvalidAppointmentTransitionException(String message) {
        super(message);
    }
}
