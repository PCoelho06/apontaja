package com.apontaja.back.customer.web;

import java.time.Instant;
import java.util.UUID;

public record CustomerHistoryItemResponse(UUID appointmentId, Instant startAt, Instant endAt, String status,
        UUID serviceId, int priceAtBookingCents, int durationAtBookingMinutes) {
}
