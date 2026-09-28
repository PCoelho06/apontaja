package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentStatus;

/** Déplacement refusé : le statut du RDV n'est ni SCHEDULED ni CONFIRMED. */
public class AppointmentNotReschedulableException extends RuntimeException {

    public AppointmentNotReschedulableException(AppointmentStatus status) {
        super("Un rendez-vous au statut " + status + " ne peut pas être déplacé");
    }
}
