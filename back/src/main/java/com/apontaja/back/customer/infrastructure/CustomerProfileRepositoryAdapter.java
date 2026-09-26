package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CustomerProfileRepositoryAdapter implements CustomerProfileRepository {

    private final CustomerProfileJpaRepository jpaRepository;

    CustomerProfileRepositoryAdapter(CustomerProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CustomerProfile save(CustomerProfile profile) {
        return jpaRepository.saveAndFlush(profile);
    }

    @Override
    public Optional<CustomerProfile> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<CustomerProfile> findAliveByIds(Collection<UUID> ids) {
        return jpaRepository.findByIdInAndDeletedAtIsNull(ids);
    }
}
