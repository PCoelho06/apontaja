package com.apontaja.back.organization.infrastructure;

import com.apontaja.back.organization.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrganizationMembershipJpaRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByAccountIdAndOrganizationIdAndDeletedAtIsNull(UUID accountId,
            UUID organizationId);

    List<OrganizationMembership> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    List<OrganizationMembership> findByAccountIdAndDeletedAtIsNull(UUID accountId);
}
