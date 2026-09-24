package com.apontaja.back.service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceResourceRepository {

    /** Flush immédiat : une collision concurrente remonte ici, pas au commit. */
    ServiceResource save(ServiceResource link);

    Optional<ServiceResource> findByServiceIdAndResourceId(UUID serviceId, UUID resourceId);

    List<ServiceResource> findByServiceId(UUID serviceId);

    void deleteByServiceIdAndResourceId(UUID serviceId, UUID resourceId);
}
