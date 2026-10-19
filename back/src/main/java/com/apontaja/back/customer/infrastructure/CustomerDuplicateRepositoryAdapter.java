package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerDuplicateRepository;
import com.apontaja.back.customer.domain.DuplicateCandidate;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
class CustomerDuplicateRepositoryAdapter implements CustomerDuplicateRepository {

    private final CustomerDuplicateJpaRepository jpaRepository;

    CustomerDuplicateRepositoryAdapter(CustomerDuplicateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<DuplicateCandidate> findCandidates(UUID salonId) {
        return jpaRepository.findCandidates(salonId);
    }

    @Override
    public Set<UUID> findProfileIdsLinkedElsewhere(UUID salonId, Collection<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jpaRepository.findProfileIdsLinkedElsewhere(salonId, profileIds));
    }
}
