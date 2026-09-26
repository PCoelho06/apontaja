package com.apontaja.back.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository {

    /** Flush immédiat, cohérent avec le reste du repo. */
    CustomerProfile save(CustomerProfile profile);

    Optional<CustomerProfile> findAliveById(UUID id);

    List<CustomerProfile> findAliveByIds(Collection<UUID> ids);
}
