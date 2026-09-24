package com.apontaja.back.service.application;

import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ServiceResourceLinkService {

    private final SalonServiceRepository serviceRepository;
    private final ServiceResourceRepository serviceResourceRepository;
    private final ResourceQueryService resourceQueryService;

    ServiceResourceLinkService(SalonServiceRepository serviceRepository,
            ServiceResourceRepository serviceResourceRepository, ResourceQueryService resourceQueryService) {
        this.serviceRepository = serviceRepository;
        this.serviceResourceRepository = serviceResourceRepository;
        this.resourceQueryService = resourceQueryService;
    }

    /** Crée l'association ou met à jour ses surcharges (upsert). */
    @Transactional
    public ServiceResourceLinkSummary link(UUID salonId, UUID serviceId, UUID resourceId, Integer overridePriceCents,
            Integer overrideDurationMinutes) {
        SalonService service = requireService(salonId, serviceId);
        if (resourceQueryService.findAliveInSalon(salonId, resourceId).isEmpty()) {
            throw new LinkedResourceNotFoundException();
        }

        ServiceResource link = serviceResourceRepository.findByServiceIdAndResourceId(serviceId, resourceId)
                .map(existing -> {
                    existing.changeOverrides(overridePriceCents, overrideDurationMinutes);
                    return existing;
                }).orElseGet(() -> new ServiceResource(serviceId, resourceId, overridePriceCents,
                        overrideDurationMinutes));

        try {
            return ServiceResourceLinkSummary.of(serviceResourceRepository.save(link), service);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceResourceLinkConflictException();
        }
    }

    /** Idempotent : supprimer une association absente n'est pas une erreur. */
    @Transactional
    public void unlink(UUID salonId, UUID serviceId, UUID resourceId) {
        requireService(salonId, serviceId);
        serviceResourceRepository.findByServiceIdAndResourceId(serviceId, resourceId)
                .ifPresent(link -> serviceResourceRepository.deleteByServiceIdAndResourceId(serviceId, resourceId));
    }

    /** Uniquement les associations dont la ressource est encore vivante. */
    @Transactional(readOnly = true)
    public List<ServiceResourceLinkSummary> list(UUID salonId, UUID serviceId) {
        SalonService service = requireService(salonId, serviceId);
        Set<UUID> aliveResourceIds = resourceQueryService.findAliveBySalonId(salonId).stream()
                .map(ResourceSummary::resourceId).collect(Collectors.toSet());

        return serviceResourceRepository.findByServiceId(serviceId).stream()
                .filter(link -> aliveResourceIds.contains(link.getResourceId()))
                .map(link -> ServiceResourceLinkSummary.of(link, service)).toList();
    }

    private SalonService requireService(UUID salonId, UUID serviceId) {
        return serviceRepository.findAliveById(serviceId).filter(s -> s.getSalonId().equals(salonId))
                .orElseThrow(ServiceNotFoundException::new);
    }
}
