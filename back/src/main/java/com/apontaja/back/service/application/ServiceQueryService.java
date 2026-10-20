package com.apontaja.back.service.application;

import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Toutes les prestations vivantes du salon avec leurs ressources vivantes
     * associées (prix/durée effectifs).
     */
    public List<ServiceOffering> findOfferingsBySalonId(UUID salonId) {
        List<UUID> resourceIds = aliveResourceIds(salonId);
        return serviceRepository.findAliveBySalonId(salonId).stream().map(s -> offering(s, resourceIds)).toList();
    }

    /** Vide si la prestation n'existe pas OU appartient à un autre salon. */
    public Optional<ServiceOffering> findOffering(UUID salonId, UUID serviceId) {
        return findService(salonId, serviceId).map(s -> offering(s, aliveResourceIds(salonId)));
    }

    private List<UUID> aliveResourceIds(UUID salonId) {
        return resourceQueryService.findAliveBySalonId(salonId).stream().map(ResourceSummary::resourceId).toList();
    }

    private ServiceOffering offering(SalonService service, List<UUID> aliveResourceIds) {
        Map<UUID, ServiceResource> linksByResource = serviceResourceRepository.findByServiceId(service.getId()).stream()
                .collect(Collectors.toMap(ServiceResource::getResourceId, Function.identity()));
        List<ResourceTerms> terms = aliveResourceIds.stream().filter(linksByResource::containsKey).map(id -> {
            ServiceResource link = linksByResource.get(id);
            return new ResourceTerms(id, ServiceResourceLinkSummary.effectivePrice(link, service),
                    ServiceResourceLinkSummary.effectiveDuration(link, service));
        }).toList();
        return new ServiceOffering(ServiceSummary.from(service), terms);
    }

    private Optional<SalonService> findService(UUID salonId, UUID serviceId) {
        return serviceRepository.findAliveById(serviceId).filter(s -> s.getSalonId().equals(salonId));
    }
}
