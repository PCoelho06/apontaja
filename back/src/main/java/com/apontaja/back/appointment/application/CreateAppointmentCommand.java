package com.apontaja.back.appointment.application;

import java.util.UUID;

/** startAt : ISO 8601 avec offset (même convention que ClosureCommand). */
public record CreateAppointmentCommand(UUID salonId, UUID customerProfileId, UUID serviceId, UUID resourceId,
        String startAt) {
}
