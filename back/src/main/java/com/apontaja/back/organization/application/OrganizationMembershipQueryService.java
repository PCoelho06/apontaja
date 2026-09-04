package com.apontaja.back.organization.application;

import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRole;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationMembershipQueryService {

    private final OrganizationMembershipRepository organizationMembershipRepository;

    OrganizationMembershipQueryService(OrganizationMembershipRepository organizationMembershipRepository) {
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    /**
     * Utilisé par d'autres domaines (ex.
     * {@code salon.application.SalonAccessGuard}) pour la règle d'accès
     * "OrganizationMembership OWNER sur l'organisation propriétaire" (§4 du
     * contexte) — sans jamais exposer {@code organization.domain} hors du domaine
     * {@code organization} (règle ArchUnit : seule {@code .application} est
     * dépendable depuis l'extérieur).
     */
    public boolean isAliveOwner(UUID accountId, UUID organizationId) {
        return organizationMembershipRepository.findAliveByAccountIdAndOrganizationId(accountId, organizationId)
                .filter(membership -> membership.getRole() == OrganizationRole.OWNER).isPresent();
    }

    /**
     * Organisations dont le compte est OWNER — utilisé pour la règle d'accès
     * élargie aux salons (voir SalonAccessGuard/SalonListQueryService) : un OWNER
     * voit tous les salons de son organisation, même sans StaffMembership explicite
     * dessus.
     */
    public List<UUID> findAliveOwnedOrganizationIds(UUID accountId) {
        return organizationMembershipRepository.findAliveByAccountId(accountId).stream()
                .filter(membership -> membership.getRole() == OrganizationRole.OWNER)
                .map(OrganizationMembership::getOrganizationId).toList();
    }
}
