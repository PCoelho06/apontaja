package com.apontaja.back.service.web;

import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(UUID serviceId, UUID salonId, String name, String description,
        int defaultDurationMinutes, int defaultPriceCents, Instant createdAt) {
}
