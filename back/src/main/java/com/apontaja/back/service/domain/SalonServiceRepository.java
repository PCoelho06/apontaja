package com.apontaja.back.service.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalonServiceRepository {

    /** Flush immédiat : une violation d'unicité remonte ici, pas au commit. */
    SalonService save(SalonService service);

    Optional<SalonService> findAliveById(UUID id);

    List<SalonService> findAliveBySalonId(UUID salonId);

    Optional<SalonService> findAliveBySalonIdAndName(UUID salonId, String name);
}
