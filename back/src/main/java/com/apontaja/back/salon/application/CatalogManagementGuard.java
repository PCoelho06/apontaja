package com.apontaja.back.salon.application;

import com.apontaja.back.organization.application.OrganizationMembershipQueryService;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Qui peut modifier le catalogue d'un salon (ressources, services, horaires,
 * fermetures) : OWNER ou MANAGER (staff direct), ou OWNER de l'organisation
 * propriétaire. EMPLOYEE : lecture seule (voir SalonAccessGuard).
 *
 * <p>
 * Appelé par nom de bean en SpEL depuis les domaines resource/service/... :
 * aucune dépendance de compilation, signature primitive uniquement.
 */
@Component("catalogManagementGuard")
public class CatalogManagementGuard {

    private final SalonRepository salonRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final OrganizationMembershipQueryService organizationMembershipQueryService;

    CatalogManagementGuard(SalonRepository salonRepository, StaffMembershipRepository staffMembershipRepository,
            OrganizationMembershipQueryService organizationMembershipQueryService) {
        this.salonRepository = salonRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.organizationMembershipQueryService = organizationMembershipQueryService;
    }

    public boolean canManageCatalog(UUID actorAccountId, UUID salonId) {
        if (actorAccountId == null || salonId == null) {
            return false;
        }
        if (isOrganizationOwner(actorAccountId, salonId)) {
            return true;
        }
        return staffMembershipRepository.findAliveByAccountIdAndSalonId(actorAccountId, salonId)
                .map(StaffMembership::getRole).map(role -> role == StaffRole.OWNER || role == StaffRole.MANAGER)
                .orElse(false);
    }

    private boolean isOrganizationOwner(UUID accountId, UUID salonId) {
        return salonRepository.findAliveById(salonId).map(Salon::getOrganizationId)
                .filter(organizationId -> organizationMembershipQueryService.isAliveOwner(accountId, organizationId))
                .isPresent();
    }
}
