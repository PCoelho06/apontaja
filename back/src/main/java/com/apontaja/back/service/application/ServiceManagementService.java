package com.apontaja.back.service.application;

import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ServiceManagementService {

    private final SalonServiceRepository serviceRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectProvider<ServiceDeletionCheck> deletionChecks;

    ServiceManagementService(SalonServiceRepository serviceRepository, IdGenerator idGenerator, Clock clock,
            ObjectProvider<ServiceDeletionCheck> deletionChecks) {
        this.serviceRepository = serviceRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.deletionChecks = deletionChecks;
    }

    @Transactional
    public ServiceSummary create(UUID salonId, ServiceDetails details) {
        String name = details.name().trim();

        if (serviceRepository.findAliveBySalonIdAndName(salonId, name).isPresent()) {
            throw new ServiceNameAlreadyUsedException();
        }

        SalonService service = new SalonService(idGenerator.generate(), salonId, name,
                normalizeDescription(details.description()), details.defaultDurationMinutes(),
                details.defaultPriceCents(), clock.instant());
        return ServiceSummary.from(saveMappingConflict(service));
    }

    @Transactional
    public ServiceSummary update(UUID salonId, UUID serviceId, ServiceDetails details) {
        String name = details.name().trim();
        SalonService service = requireInSalon(salonId, serviceId);

        serviceRepository.findAliveBySalonIdAndName(salonId, name).filter(other -> !other.getId().equals(serviceId))
                .ifPresent(other -> {
                    throw new ServiceNameAlreadyUsedException();
                });

        service.update(name, normalizeDescription(details.description()), details.defaultDurationMinutes(),
                details.defaultPriceCents());
        return ServiceSummary.from(saveMappingConflict(service));
    }

    @Transactional
    public void delete(UUID salonId, UUID serviceId) {
        SalonService service = requireInSalon(salonId, serviceId);

        if (deletionChecks.orderedStream().anyMatch(check -> check.hasBlockingAppointments(serviceId))) {
            throw new ServiceInUseException();
        }

        service.softDelete(clock.instant());
        serviceRepository.save(service);
    }

    private SalonService requireInSalon(UUID salonId, UUID serviceId) {
        return serviceRepository.findAliveById(serviceId).filter(s -> s.getSalonId().equals(salonId))
                .orElseThrow(ServiceNotFoundException::new);
    }

    /** Filet de sécurité : l'index unique partiel gagne face à une course. */
    private SalonService saveMappingConflict(SalonService service) {
        try {
            return serviceRepository.save(service);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceNameAlreadyUsedException();
        }
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
