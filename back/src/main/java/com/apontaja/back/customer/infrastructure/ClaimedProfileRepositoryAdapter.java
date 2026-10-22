package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.ClaimedProfileRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class ClaimedProfileRepositoryAdapter implements ClaimedProfileRepository {

    private final ClaimedProfileJpaRepository jpaRepository;

    ClaimedProfileRepositoryAdapter(ClaimedProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UUID> findClaimedProfileId(UUID accountId, UUID salonId) {
        return jpaRepository.findProfileIds(accountId, salonId).stream().findFirst();
    }
}
