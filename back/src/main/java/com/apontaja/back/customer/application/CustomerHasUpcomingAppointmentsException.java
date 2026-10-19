package com.apontaja.back.customer.application;

public class CustomerHasUpcomingAppointmentsException extends RuntimeException {

    public CustomerHasUpcomingAppointmentsException() {
        super("Ce client a des rendez-vous à venir : annulez-les avant de l'anonymiser.");
    }
}
