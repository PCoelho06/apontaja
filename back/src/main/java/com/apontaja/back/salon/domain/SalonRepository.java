package com.apontaja.back.salon.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalonRepository {

    Salon save(Salon salon);

    Optional<Salon> findAliveById(UUID id);

    List<Salon> findAliveByOrganizationId(UUID organizationId);
}
