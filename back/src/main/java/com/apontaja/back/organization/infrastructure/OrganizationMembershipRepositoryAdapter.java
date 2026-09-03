package com.apontaja.back.organization.infrastructure;

import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OrganizationMembershipRepositoryAdapter implements OrganizationMembershipRepository {

    private final OrganizationMembershipJpaRepository jpaRepository;

    OrganizationMembershipRepositoryAdapter(OrganizationMembershipJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OrganizationMembership save(OrganizationMembership membership) {
        return jpaRepository.save(membership);
    }

    @Override
    public Optional<OrganizationMembership> findAliveByAccountIdAndOrganizationId(UUID accountId, UUID organizationId) {
        return jpaRepository.findByAccountIdAndOrganizationIdAndDeletedAtIsNull(accountId, organizationId);
    }

    @Override
    public List<OrganizationMembership> findAliveByOrganizationId(UUID organizationId) {
        return jpaRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Override
    public List<OrganizationMembership> findAliveByAccountId(UUID accountId) {
        return jpaRepository.findByAccountIdAndDeletedAtIsNull(accountId);
    }
}
