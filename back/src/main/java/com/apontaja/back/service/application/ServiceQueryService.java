package com.apontaja.back.service.application;

import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ServiceQueryService {

    private final SalonServiceRepository serviceRepository;
    private final ServiceResourceRepository serviceResourceRepository;
    private final ResourceQueryService resourceQueryService;

    ServiceQueryService(SalonServiceRepository serviceRepository, ServiceResourceRepository serviceResourceRepository,
            ResourceQueryService resourceQueryService) {
        this.serviceRepository = serviceRepository;
        this.serviceResourceRepository = serviceResourceRepository;
        this.resourceQueryService = resourceQueryService;
    }

    public List<ServiceSummary> findAliveBySalonId(UUID salonId) {
        return serviceRepository.findAliveBySalonId(salonId).stream().map(ServiceSummary::from).toList();
    }

    /** Vide si la prestation n'existe pas OU appartient à un autre salon. */
    public Optional<ServiceSummary> findAliveInSalon(UUID salonId, UUID serviceId) {
        return findService(salonId, serviceId).map(ServiceSummary::from);
    }

    /**
     * Prix/durée effectifs pour réaliser ce service avec cette ressource (base du
     * snapshot d'un RDV). Vide si le service ou la ressource n'existe plus dans ce
     * salon, ou si la ressource n'est pas associée au service.
     */
    public Optional<ServiceTerms> resolveTerms(UUID salonId, UUID serviceId, UUID resourceId) {
        Optional<SalonService> service = findService(salonId, serviceId);
        if (service.isEmpty() || resourceQueryService.findAliveInSalon(salonId, resourceId).isEmpty()) {
            return Optional.empty();
        }
        SalonService salonService = service.get();
        return serviceResourceRepository.findByServiceIdAndResourceId(serviceId, resourceId)
                .map(link -> new ServiceTerms(ServiceResourceLinkSummary.effectivePrice(link, salonService),
                        ServiceResourceLinkSummary.effectiveDuration(link, salonService)));
    }

    private Optional<SalonService> findService(UUID salonId, UUID serviceId) {
        return serviceRepository.findAliveById(serviceId).filter(s -> s.getSalonId().equals(salonId));
    }
}
