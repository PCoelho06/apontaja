package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Closure;

import java.time.Instant;
import java.util.UUID;

public record ClosureSummary(UUID closureId, UUID salonId, UUID resourceId, Instant startAt, Instant endAt,
        String reason, Instant createdAt) {

    static ClosureSummary from(Closure c) {
        return new ClosureSummary(c.getId(), c.getSalonId(), c.getResourceId(), c.getStartAt(), c.getEndAt(),
                c.getReason(), c.getCreatedAt());
    }
}
