package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ClosureRepositoryAdapter implements ClosureRepository {

    private final ClosureJpaRepository jpaRepository;

    ClosureRepositoryAdapter(ClosureJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Closure save(Closure closure) {
        return jpaRepository.saveAndFlush(closure);
    }

    @Override
    public Optional<Closure> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Closure> findOverlappingBySalonId(UUID salonId, Instant from, Instant to) {
        return jpaRepository.findOverlappingBySalonId(salonId, from, to);
    }

    @Override
    public List<Closure> findOverlappingByResourceId(UUID resourceId, Instant from, Instant to) {
        return jpaRepository.findOverlappingByResourceId(resourceId, from, to);
    }

    @Override
    public void delete(Closure closure) {
        jpaRepository.delete(closure);
        jpaRepository.flush();
    }
}
