package com.apontaja.back.organization.application;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.organization.domain.OrganizationRole;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Un compte n'a jamais qu'une seule organisation (règle applicative — aucune
 * contrainte DB ne l'impose, {@code ux_org_membership_alive} n'unique que la
 * paire (account_id, organization_id)). Tous les salons créés par un même
 * compte sont rattachés à cette organisation unique.
 */
@Service
public class OrganizationProvisioningService {

    private static final String DEFAULT_ORGANIZATION_NAME = "Mon organisation";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    OrganizationProvisioningService(OrganizationRepository organizationRepository,
            OrganizationMembershipRepository organizationMembershipRepository, IdGenerator idGenerator, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Retourne l'organisation existante du compte, ou en crée une (avec membership
     * OWNER) sinon.
     */
    @Transactional
    public UUID ensureOrganizationForAccount(UUID accountId) {
        return organizationMembershipRepository.findAliveByAccountId(accountId).stream().findFirst()
                .map(OrganizationMembership::getOrganizationId).orElseGet(() -> createOrganization(accountId));
    }

    private UUID createOrganization(UUID accountId) {
        Instant now = clock.instant();

        Organization organization = new Organization(idGenerator.generate(), DEFAULT_ORGANIZATION_NAME, now);
        organizationRepository.save(organization);

        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organization.getId(), OrganizationRole.OWNER, now));

        return organization.getId();
    }
}
