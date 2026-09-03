package com.apontaja.back.organization.domain;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    Organization save(Organization organization);

    Optional<Organization> findAliveById(UUID id);
}
