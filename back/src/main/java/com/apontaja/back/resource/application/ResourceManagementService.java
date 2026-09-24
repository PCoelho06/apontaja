package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ResourceManagementService {

    private final ResourceRepository resourceRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectProvider<ResourceDeletionCheck> deletionChecks;

    ResourceManagementService(ResourceRepository resourceRepository, IdGenerator idGenerator, Clock clock,
            ObjectProvider<ResourceDeletionCheck> deletionChecks) {
        this.resourceRepository = resourceRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.deletionChecks = deletionChecks;
    }

    @Transactional
    public ResourceSummary create(CreateResourceCommand command) {
        ResourceType type = parseType(command.type());
        String name = command.name().trim();

        if (resourceRepository.findAliveBySalonIdAndName(command.salonId(), name).isPresent()) {
            throw new ResourceNameAlreadyUsedException();
        }

        Resource resource = new Resource(idGenerator.generate(), command.salonId(), name, type, clock.instant());
        return ResourceSummary.from(saveMappingConflict(resource));
    }

    @Transactional
    public ResourceSummary update(UUID salonId, UUID resourceId, String newName, String newType) {
        ResourceType type = parseType(newType);
        String name = newName.trim();
        Resource resource = requireInSalon(salonId, resourceId);

        resourceRepository.findAliveBySalonIdAndName(salonId, name).filter(other -> !other.getId().equals(resourceId))
                .ifPresent(other -> {
                    throw new ResourceNameAlreadyUsedException();
                });

        resource.update(name, type);
        return ResourceSummary.from(saveMappingConflict(resource));
    }

    @Transactional
    public void delete(UUID salonId, UUID resourceId) {
        Resource resource = requireInSalon(salonId, resourceId);

        if (deletionChecks.orderedStream().anyMatch(check -> check.hasBlockingAppointments(resourceId))) {
            throw new ResourceInUseException();
        }

        resource.softDelete(clock.instant());
        resourceRepository.save(resource);
    }

    private Resource requireInSalon(UUID salonId, UUID resourceId) {
        return resourceRepository.findAliveById(resourceId).filter(r -> r.getSalonId().equals(salonId))
                .orElseThrow(ResourceNotFoundException::new);
    }

    /** Filet de sécurité : l'index unique partiel gagne face à une course. */
    private Resource saveMappingConflict(Resource resource) {
        try {
            return resourceRepository.save(resource);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceNameAlreadyUsedException();
        }
    }

    private static ResourceType parseType(String type) {
        try {
            return ResourceType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidResourceTypeException(type);
        }
    }
}
