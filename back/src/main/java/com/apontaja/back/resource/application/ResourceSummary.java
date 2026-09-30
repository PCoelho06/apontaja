package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;

import java.time.Instant;
import java.util.UUID;

// après
public record ResourceSummary(UUID resourceId, UUID salonId, String name, String type, UUID staffMembershipId,
        Instant createdAt) {

    /**
     * Sans lien vers un membre de l'équipe : conserve la compatibilité des
     * appelants existants.
     */
    public ResourceSummary(UUID resourceId, UUID salonId, String name, String type, Instant createdAt) {
        this(resourceId, salonId, name, type, null, createdAt);
    }

    static ResourceSummary from(Resource resource) {
        return new ResourceSummary(resource.getId(), resource.getSalonId(), resource.getName(),
                resource.getType().name(), resource.getStaffMembershipId(), resource.getCreatedAt());
    }
}
