package com.apontaja.back.service.infrastructure;

import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class SalonServiceRepositoryAdapter implements SalonServiceRepository {

    private final SalonServiceJpaRepository jpaRepository;

    SalonServiceRepositoryAdapter(SalonServiceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SalonService save(SalonService service) {
        return jpaRepository.saveAndFlush(service);
    }

    @Override
    public Optional<SalonService> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<SalonService> findAliveBySalonId(UUID salonId) {
        return jpaRepository.findBySalonIdAndDeletedAtIsNullOrderByNameAsc(salonId);
    }

    @Override
    public Optional<SalonService> findAliveBySalonIdAndName(UUID salonId, String name) {
        return jpaRepository.findBySalonIdAndNameAndDeletedAtIsNull(salonId, name);
    }
}
