package com.apontaja.back.resource.web;

import java.time.Instant;
import java.util.UUID;

import com.apontaja.back.resource.application.BlockingAppointment;

record BlockingAppointmentResponse(UUID appointmentId, UUID resourceId, Instant startAt, Instant endAt) {

    static BlockingAppointmentResponse from(BlockingAppointment a) {
        return new BlockingAppointmentResponse(a.appointmentId(), a.resourceId(), a.startAt(), a.endAt());
    }
}
