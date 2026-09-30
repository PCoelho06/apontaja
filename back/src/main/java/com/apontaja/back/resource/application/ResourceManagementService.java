package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.application.StaffMemberQueryService;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ResourceManagementService {

    /**
     * Index unique partiel V5 : sert à distinguer ce conflit d'un conflit de nom.
     */
    private static final String STAFF_LINK_INDEX = "uq_resource_staff_membership_alive";

    private final ResourceRepository resourceRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectProvider<ResourceDeletionCheck> deletionChecks;
    private final StaffMemberQueryService staffMemberQueryService;

    ResourceManagementService(ResourceRepository resourceRepository, IdGenerator idGenerator, Clock clock,
            ObjectProvider<ResourceDeletionCheck> deletionChecks, StaffMemberQueryService staffMemberQueryService) {
        this.resourceRepository = resourceRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.deletionChecks = deletionChecks;
        this.staffMemberQueryService = staffMemberQueryService;
    }

    /** Création sans lien vers un membre de l'équipe. */
    @Transactional
    public ResourceSummary create(CreateResourceCommand command) {
        return create(command, null);
    }

    @Transactional
    public ResourceSummary create(CreateResourceCommand command, UUID staffMembershipId) {
        ResourceType type = parseType(command.type());
        requireLinkCompatibleWithType(type, staffMembershipId);
        requireStaffMemberInSalon(command.salonId(), staffMembershipId);
        String name = command.name().trim();

        if (resourceRepository.findAliveBySalonIdAndName(command.salonId(), name).isPresent()) {
            throw new ResourceNameAlreadyUsedException();
        }
        requireStaffMemberNotLinked(command.salonId(), staffMembershipId, null);

        Resource resource = new Resource(idGenerator.generate(), command.salonId(), name, type, clock.instant());
        resource.linkToStaff(staffMembershipId);
        return ResourceSummary.from(saveMappingConflict(resource));
    }

    /** Mise à jour SANS lien : délie la ressource (PUT = remplacement complet). */
    @Transactional
    public ResourceSummary update(UUID salonId, UUID resourceId, String newName, String newType) {
        return update(salonId, resourceId, newName, newType, null);
    }

    @Transactional
    public ResourceSummary update(UUID salonId, UUID resourceId, String newName, String newType,
            UUID staffMembershipId) {
        ResourceType type = parseType(newType);
        requireLinkCompatibleWithType(type, staffMembershipId);
        String name = newName.trim();
        Resource resource = requireInSalon(salonId, resourceId);
        requireStaffMemberInSalon(salonId, staffMembershipId);

        resourceRepository.findAliveBySalonIdAndName(salonId, name).filter(other -> !other.getId().equals(resourceId))
                .ifPresent(other -> {
                    throw new ResourceNameAlreadyUsedException();
                });
        requireStaffMemberNotLinked(salonId, staffMembershipId, resourceId);

        resource.update(name, type);
        resource.linkToStaff(staffMembershipId);
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

    /**
     * Filet de sécurité : les index uniques partiels (nom, lien staff) gagnent face
     * à une course.
     */
    private Resource saveMappingConflict(Resource resource) {
        try {
            return resourceRepository.save(resource);
        } catch (DataIntegrityViolationException e) {
            if (isStaffLinkViolation(e)) {
                throw new StaffMemberAlreadyLinkedException();
            }
            throw new ResourceNameAlreadyUsedException();
        }
    }

    private static boolean isStaffLinkViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(STAFF_LINK_INDEX);
    }

    private static void requireLinkCompatibleWithType(ResourceType type, UUID staffMembershipId) {
        if (staffMembershipId != null && type != ResourceType.EMPLOYEE) {
            throw new InvalidResourceStaffLinkException();
        }
    }

    private void requireStaffMemberInSalon(UUID salonId, UUID staffMembershipId) {
        if (staffMembershipId != null && !staffMemberQueryService.existsAliveInSalon(salonId, staffMembershipId)) {
            throw new LinkedStaffMemberNotFoundException();
        }
    }

    /**
     * Un membre n'est lié qu'à une ressource vivante (l'index V5 couvre la
     * concurrence).
     */
    private void requireStaffMemberNotLinked(UUID salonId, UUID staffMembershipId, UUID exceptResourceId) {
        if (staffMembershipId == null) {
            return;
        }
        boolean linkedElsewhere = resourceRepository.findAliveBySalonId(salonId).stream().anyMatch(
                r -> staffMembershipId.equals(r.getStaffMembershipId()) && !r.getId().equals(exceptResourceId));
        if (linkedElsewhere) {
            throw new StaffMemberAlreadyLinkedException();
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
