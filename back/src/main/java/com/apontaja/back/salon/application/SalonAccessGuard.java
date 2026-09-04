package com.apontaja.back.salon.application;

import com.apontaja.back.organization.application.OrganizationMembershipQueryService;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembershipRepository;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'entrée unique pour l'autorisation centralisée par method security
 * ({@code @PreAuthorize}) sur le domaine salon — conforme à la décision
 * [DECIDED] du contexte ("autorisation centralisée via method security, jamais
 * de checks ad hoc dispersés").
 *
 * <p>
 * Règle d'accès à un salon (§4 du contexte) : {@code StaffMembership} actif sur
 * ce salon, OU {@code OrganizationMembership} OWNER sur l'organisation
 * propriétaire du salon — cette seconde partie est déléguée à
 * {@code organization.application.OrganizationMembershipQueryService} plutôt
 * que d'importer {@code organization.domain} directement (règle ArchUnit :
 * seule {@code .application} d'un domaine est dépendable depuis l'extérieur).
 */
@Component("salonAccessGuard")
public class SalonAccessGuard {

    private final SalonRepository salonRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final OrganizationMembershipQueryService organizationMembershipQueryService;

    SalonAccessGuard(SalonRepository salonRepository, StaffMembershipRepository staffMembershipRepository,
            OrganizationMembershipQueryService organizationMembershipQueryService) {
        this.salonRepository = salonRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.organizationMembershipQueryService = organizationMembershipQueryService;
    }

    /**
     * {@code false} si le salon n'existe pas ou a été soft-deleted — pas de
     * distinction avec "pas d'accès" (403 dans tous les cas côté contrôleur), pour
     * ne pas confirmer l'existence d'un salon à un compte qui n'y a pas accès.
     */
    public boolean hasAccessToSalon(UUID accountId, UUID salonId) {
        if (accountId == null || salonId == null) {
            return false;
        }

        if (staffMembershipRepository.findAliveByAccountIdAndSalonId(accountId, salonId).isPresent()) {
            return true;
        }

        return salonRepository.findAliveById(salonId).map(Salon::getOrganizationId)
                .filter(organizationId -> organizationMembershipQueryService.isAliveOwner(accountId, organizationId))
                .isPresent();
    }
}
