package com.apontaja.back.customer.application;

import java.time.Instant;
import java.util.UUID;

public record CustomerHistoryItem(UUID appointmentId, Instant startAt, Instant endAt, String status,
        UUID serviceId, int priceAtBookingCents, int durationAtBookingMinutes) {
}
