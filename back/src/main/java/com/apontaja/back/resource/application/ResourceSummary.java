package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;

import java.time.Instant;
import java.util.UUID;

public record ResourceSummary(UUID resourceId, UUID salonId, String name, String type, Instant createdAt) {

    static ResourceSummary from(Resource resource) {
        return new ResourceSummary(resource.getId(), resource.getSalonId(), resource.getName(),
                resource.getType().name(), resource.getCreatedAt());
    }
}
