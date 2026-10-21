package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.ClaimCandidate;
import com.apontaja.back.customer.domain.CustomerClaimRepository;

import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
class CustomerClaimRepositoryAdapter implements CustomerClaimRepository {

    private final CustomerClaimJpaRepository jpaRepository;

    CustomerClaimRepositoryAdapter(CustomerClaimJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ClaimCandidate> findClaimCandidates(String email) {
        return jpaRepository.findCandidatesByEmail(email);
    }

    @Override
    public Set<UUID> findSalonIdsHoldingClaimedProfile(UUID accountId) {
        return new HashSet<>(jpaRepository.findSalonIdsWithClaimedProfile(accountId));
    }
}
