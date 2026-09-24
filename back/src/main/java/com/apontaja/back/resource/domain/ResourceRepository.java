package com.apontaja.back.resource.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    /** Flush immédiat : une violation d'unicité remonte ici, pas au commit. */
    Resource save(Resource resource);

    Optional<Resource> findAliveById(UUID id);

    List<Resource> findAliveBySalonId(UUID salonId);

    Optional<Resource> findAliveBySalonIdAndName(UUID salonId, String name);
}
