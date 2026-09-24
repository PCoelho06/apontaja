package com.apontaja.back.resource.web;

import java.time.Instant;
import java.util.UUID;

public record ClosureResponse(UUID closureId, UUID salonId, UUID resourceId, Instant startAt, Instant endAt,
        String reason, Instant createdAt) {
}
