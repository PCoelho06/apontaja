package com.apontaja.back.appointment.application;

public class BookingQuotaExceededException extends RuntimeException {

    public BookingQuotaExceededException(int max) {
        super("Vous avez déjà " + max + " rendez-vous à venir dans ce salon.");
    }
}
