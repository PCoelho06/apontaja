package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ResourceRepositoryAdapter implements ResourceRepository {

    private final ResourceJpaRepository jpaRepository;

    ResourceRepositoryAdapter(ResourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Resource save(Resource resource) {
        return jpaRepository.saveAndFlush(resource);
    }

    @Override
    public Optional<Resource> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Resource> findAliveBySalonId(UUID salonId) {
        return jpaRepository.findBySalonIdAndDeletedAtIsNullOrderByNameAsc(salonId);
    }

    @Override
    public Optional<Resource> findAliveBySalonIdAndName(UUID salonId, String name) {
        return jpaRepository.findBySalonIdAndNameAndDeletedAtIsNull(salonId, name);
    }
}
