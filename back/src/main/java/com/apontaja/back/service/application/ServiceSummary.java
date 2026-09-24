package com.apontaja.back.service.application;

import com.apontaja.back.service.domain.SalonService;

import java.time.Instant;
import java.util.UUID;

public record ServiceSummary(UUID serviceId, UUID salonId, String name, String description,
        int defaultDurationMinutes, int defaultPriceCents, Instant createdAt) {

    static ServiceSummary from(SalonService s) {
        return new ServiceSummary(s.getId(), s.getSalonId(), s.getName(), s.getDescription(),
                s.getDefaultDurationMinutes(), s.getDefaultPriceCents(), s.getCreatedAt());
    }
}
