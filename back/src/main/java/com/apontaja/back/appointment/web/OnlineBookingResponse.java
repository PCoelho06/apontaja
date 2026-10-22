package com.apontaja.back.appointment.web;

import java.time.Instant;
import java.util.UUID;

public record OnlineBookingResponse(UUID appointmentId, UUID salonId, UUID serviceId, String serviceName,
        UUID resourceId, String resourceName, Instant startAt, Instant endAt, String status, int priceCents,
        int durationMinutes, Instant createdAt) {
}
