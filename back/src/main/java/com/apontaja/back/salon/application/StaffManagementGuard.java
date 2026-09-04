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
 * Règles de gestion du staff : OWNER (staff direct ou OrganizationMembership
 * OWNER) gère n'importe qui ; MANAGER ne gère que des EMPLOYEE ; EMPLOYEE ne
 * gère personne (lecture seule, voir SalonAccessGuard pour la consultation).
 *
 * <p>
 * N'expose que des méthodes à signature primitive ({@code String} pour le rôle,
 * jamais {@code StaffRole}) — appelé depuis {@code @PreAuthorize} en SpEL
 * depuis {@code salon.web}, qui n'a pas le droit d'importer
 * {@code salon.domain} (règle de couches, y compris au sein d'un même domaine).
 */
@Component("staffManagementGuard")
public class StaffManagementGuard {

    private final SalonRepository salonRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final OrganizationMembershipQueryService organizationMembershipQueryService;

    StaffManagementGuard(SalonRepository salonRepository, StaffMembershipRepository staffMembershipRepository,
            OrganizationMembershipQueryService organizationMembershipQueryService) {
        this.salonRepository = salonRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.organizationMembershipQueryService = organizationMembershipQueryService;
    }

    /**
     * {@code false} si {@code targetRoleName} n'est pas un rôle valide — échec
     * fermé par défaut.
     */
    public boolean canManageRole(UUID actorAccountId, UUID salonId, String targetRoleName) {
        if (actorAccountId == null || salonId == null) {
            return false;
        }

        StaffRole targetRole = parseRoleOrNull(targetRoleName);
        if (targetRole == null) {
            return false;
        }

        if (isOrganizationOwner(actorAccountId, salonId)) {
            return true;
        }

        return staffMembershipRepository.findAliveByAccountIdAndSalonId(actorAccountId, salonId)
                .map(StaffMembership::getRole).map(actorRole -> canRoleManage(actorRole, targetRole)).orElse(false);
    }

    private static boolean canRoleManage(StaffRole actorRole, StaffRole targetRole) {
        return switch (actorRole) {
        case OWNER -> true;
        case MANAGER -> targetRole == StaffRole.EMPLOYEE;
        case EMPLOYEE -> false;
        };
    }

    private boolean isOrganizationOwner(UUID accountId, UUID salonId) {
        return salonRepository.findAliveById(salonId).map(Salon::getOrganizationId)
                .filter(organizationId -> organizationMembershipQueryService.isAliveOwner(accountId, organizationId))
                .isPresent();
    }

    private static StaffRole parseRoleOrNull(String roleName) {
        try {
            return StaffRole.valueOf(roleName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
