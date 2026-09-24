package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.ResourceRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResourceQueryService {

    private final ResourceRepository resourceRepository;

    ResourceQueryService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    public List<ResourceSummary> findAliveBySalonId(UUID salonId) {
        return resourceRepository.findAliveBySalonId(salonId).stream().map(ResourceSummary::from).toList();
    }

    /** Vide si la ressource n'existe pas OU appartient à un autre salon. */
    public Optional<ResourceSummary> findAliveInSalon(UUID salonId, UUID resourceId) {
        return resourceRepository.findAliveById(resourceId).filter(r -> r.getSalonId().equals(salonId))
                .map(ResourceSummary::from);
    }
}
