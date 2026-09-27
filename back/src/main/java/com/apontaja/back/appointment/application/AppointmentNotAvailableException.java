package com.apontaja.back.appointment.application;

import com.apontaja.back.resource.application.AvailabilityIssue;

import java.util.List;

public class AppointmentNotAvailableException extends RuntimeException {

    private final List<AvailabilityIssue> issues;

    public AppointmentNotAvailableException(List<AvailabilityIssue> issues) {
        super("Ce créneau n'est pas réservable sur cette ressource.");
        this.issues = List.copyOf(issues);
    }

    public List<AvailabilityIssue> issues() {
        return issues;
    }
}
