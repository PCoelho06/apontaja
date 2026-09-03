package com.apontaja.back.organization.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository {

    OrganizationMembership save(OrganizationMembership membership);

    Optional<OrganizationMembership> findAliveByAccountIdAndOrganizationId(UUID accountId, UUID organizationId);

    List<OrganizationMembership> findAliveByOrganizationId(UUID organizationId);

    List<OrganizationMembership> findAliveByAccountId(UUID accountId);
}
