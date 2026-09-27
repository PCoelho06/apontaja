package com.apontaja.back.appointment.web;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(UUID appointmentId, UUID salonId, UUID customerProfileId, UUID serviceId,
        UUID resourceId, Instant startAt, Instant endAt, String status, int priceAtBookingCents,
        int durationAtBookingMinutes, Instant createdAt) {
}
