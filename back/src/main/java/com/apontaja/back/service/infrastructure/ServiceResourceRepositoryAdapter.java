package com.apontaja.back.service.infrastructure;

import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceId;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ServiceResourceRepositoryAdapter implements ServiceResourceRepository {

    private final ServiceResourceJpaRepository jpaRepository;

    ServiceResourceRepositoryAdapter(ServiceResourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ServiceResource save(ServiceResource link) {
        return jpaRepository.saveAndFlush(link);
    }

    @Override
    public Optional<ServiceResource> findByServiceIdAndResourceId(UUID serviceId, UUID resourceId) {
        return jpaRepository.findById(new ServiceResourceId(serviceId, resourceId));
    }

    @Override
    public List<ServiceResource> findByServiceId(UUID serviceId) {
        return jpaRepository.findByServiceId(serviceId);
    }

    @Override
    public void deleteByServiceIdAndResourceId(UUID serviceId, UUID resourceId) {
        jpaRepository.deleteById(new ServiceResourceId(serviceId, resourceId));
        jpaRepository.flush();
    }
}
