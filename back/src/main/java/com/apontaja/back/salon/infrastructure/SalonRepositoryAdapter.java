package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class SalonRepositoryAdapter implements SalonRepository {

    private final SalonJpaRepository jpaRepository;

    SalonRepositoryAdapter(SalonJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Salon save(Salon salon) {
        return jpaRepository.save(salon);
    }

    @Override
    public Optional<Salon> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Salon> findAliveByOrganizationId(UUID organizationId) {
        return jpaRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Override
    public List<Salon> findAliveByIds(Collection<UUID> ids) {
        return jpaRepository.findByIdInAndDeletedAtIsNull(ids);
    }
}
